package com.itheima.service.impl;

import com.itheima.mapper.BrowseLogMapper;
import com.itheima.mapper.UserActionMapper;
import com.itheima.mapper.UserProfileMapper;
import com.itheima.pojo.CategoryStat;
import com.itheima.pojo.ProfileInterest;
import com.itheima.pojo.UserProfile;
import com.itheima.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    /** 画像统计窗口：近 7 天（与阶段 A 埋点数据的自然节奏一致） */
    private static final int WINDOW_DAYS = 7;
    /** 活跃时段只保留前 3 个 */
    private static final int ACTIVE_HOUR_LIMIT = 3;
    /** 停留时长折算基准：30 秒算一档 */
    private static final double DWELL_UNIT_MS = 30000.0;

    private final BrowseLogMapper browseLogMapper;
    private final UserActionMapper userActionMapper;
    private final UserProfileMapper userProfileMapper;

    @Override
    public UserProfile get(Long userId) {
        UserProfile profile = userProfileMapper.findProfile(userId);
        if (profile == null) {
            return null;
        }
        profile.setInterests(userProfileMapper.findInterests(userId));
        return profile;
    }

    /**
     * 生成 = 聚合 → 打分 → 归一化 → 覆盖写入。
     * 主表与权重明细必须一起更新，否则会出现「画像表在、权重行没了」的中间态。
     */
    @Override
    @Transactional
    public UserProfile generate(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(WINDOW_DAYS);
        List<CategoryStat> browseStats = browseLogMapper.statsByCategory(userId, since);
        List<CategoryStat> actionStats = userActionMapper.actionStatsByCategory(userId, since);
        Map<Long, CategoryStat> merged = merge(browseStats, actionStats);

        if (merged.isEmpty()) {
            userProfileMapper.deleteInterests(userId);
            userProfileMapper.deleteProfile(userId);
            return null;
        }

        // 规则打分（刻意朴素、可解释）：浏览 1 分 + 互动 3 分 + 平均停留每 30 秒折算 2 分。
        // 阶段 B' 的目标是先把「聚合 → 画像 → 读出」这条链路跑通并留好降级路径，
        // 打分权重本身由阶段 C 的评估结果来调，而不是现在拍脑袋做复杂模型。
        double total = 0;
        Map<Long, Double> rawScores = new HashMap<>();
        for (CategoryStat stat : merged.values()) {
            double raw = nz(stat.getPv())
                    + 3.0 * nz(stat.getActions())
                    + (nz(stat.getAvgDwellMs()) / DWELL_UNIT_MS) * 2.0;
            raw = Math.max(raw, 0.0001);   // 防全 0 频道把权重算成 NaN
            rawScores.put(stat.getCategoryId(), raw);
            total += raw;
        }

        List<ProfileInterest> interests = new ArrayList<>();
        int sampleSize = 0;
        for (CategoryStat stat : merged.values()) {
            ProfileInterest interest = new ProfileInterest();
            interest.setUserId(userId);
            interest.setCategoryId(stat.getCategoryId());
            interest.setCategoryName(stat.getCategoryName() == null ? "" : stat.getCategoryName());
            interest.setWeight(BigDecimal.valueOf(rawScores.get(stat.getCategoryId()) / total)
                    .setScale(4, RoundingMode.HALF_UP));
            interest.setPv(nz(stat.getPv()));
            interest.setAvgDwellMs(nz(stat.getAvgDwellMs()));
            interest.setActions(nz(stat.getActions()));
            interests.add(interest);
            sampleSize += nz(stat.getPv()) + nz(stat.getActions());
        }
        interests.sort((a, b) -> b.getWeight().compareTo(a.getWeight()));

        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setActiveHours(joinHours(browseLogMapper.activeHours(userId, since, ACTIVE_HOUR_LIMIT)));
        profile.setSource("rule");
        profile.setSampleSize(sampleSize);
        userProfileMapper.upsertProfile(profile);
        userProfileMapper.deleteInterests(userId);
        userProfileMapper.insertInterests(interests);

        // 读回一次：把库里 NOW() 生成的 generatedAt 与排序后的权重返回给调用方
        return get(userId);
    }

    @Override
    public List<Long> activeUserIds(int days) {
        return browseLogMapper.activeUserIds(LocalDateTime.now().minusDays(days));
    }

    /** 按频道合并浏览统计与互动统计（任一侧缺失都要保留另一侧） */
    private Map<Long, CategoryStat> merge(List<CategoryStat> browseStats, List<CategoryStat> actionStats) {
        Map<Long, CategoryStat> merged = new LinkedHashMap<>();
        for (CategoryStat stat : browseStats) {
            stat.setActions(0);
            merged.put(stat.getCategoryId(), stat);
        }
        for (CategoryStat action : actionStats) {
            CategoryStat target = merged.get(action.getCategoryId());
            if (target == null) {
                action.setPv(0);
                action.setAvgDwellMs(0);
                merged.put(action.getCategoryId(), action);
            } else {
                target.setActions(action.getActions());
            }
        }
        return merged;
    }

    private static String joinHours(List<Integer> hours) {
        if (hours == null || hours.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Integer hour : hours) {
            if (hour == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(hour);
        }
        return sb.toString();
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
