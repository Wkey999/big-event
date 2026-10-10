package com.itheima.service.impl;

import com.itheima.exception.BusinessException;
import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.CategoryMapper;
import com.itheima.mapper.UserActionMapper;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.Article;
import com.itheima.pojo.ArticleAddDTO;
import com.itheima.pojo.ArticleUpdateDTO;
import com.itheima.pojo.Category;
import com.itheima.pojo.PageBean;
import com.itheima.pojo.ProfileInterest;
import com.itheima.pojo.User;
import com.itheima.pojo.UserProfile;
import com.itheima.service.ArticleService;
import com.itheima.service.UserProfileService;
import com.itheima.utils.ActionTypes;
import com.itheima.utils.ThreadLocalUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private static final int RECOMMEND_CANDIDATE_LIMIT = 100;
    private static final int RECOMMEND_RESULT_LIMIT = 30;
    private static final int MIN_PROFILE_SAMPLE_SIZE = 3;
    private static final double UNSEEN_CATEGORY_WEIGHT = 0.02;
    private static final double NEW_ARTICLE_BOOST = 1.5;
    private static final double TIME_DECAY_HALF_LIFE_HOURS = 24.0 * 7.0;

    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final UserActionMapper userActionMapper;
    private final UserProfileService userProfileService;

    @Override
    @Transactional
    public void add(ArticleAddDTO dto) {
        Long userId = ThreadLocalUtils.getUserId();
        assertCategoryExists(dto.getCategoryId());
        Article article = new Article();
        article.setTitle(dto.getTitle());
        article.setContent(dto.getContent());
        article.setCoverImg(emptyIfNull(dto.getCoverImg()));
        article.setSummary(emptyIfNull(dto.getSummary()));
        article.setCategoryId(dto.getCategoryId());
        article.setState(dto.getState());
        article.setUserId(userId);
        articleMapper.insert(article);
    }

    @Override
    @Transactional
    public void update(ArticleUpdateDTO dto) {
        Long userId = ThreadLocalUtils.getUserId();
        assertCategoryExists(dto.getCategoryId());
        Article article = new Article();
        article.setId(dto.getId());
        article.setTitle(dto.getTitle());
        article.setContent(dto.getContent());
        article.setCoverImg(emptyIfNull(dto.getCoverImg()));
        article.setSummary(emptyIfNull(dto.getSummary()));
        article.setCategoryId(dto.getCategoryId());
        article.setState(dto.getState());

        int rows;
        if (isAdmin()) {
            // 管理员可改任意文章，且不改变原作者（updateAny 不动 user_id）
            rows = articleMapper.updateAny(article);
        } else {
            // 普通用户只能改自己的：WHERE 带 user_id，越权/不存在影响 0 行
            article.setUserId(userId);
            rows = articleMapper.update(article);
        }
        if (rows == 0) {
            throw new BusinessException("更新失败：文章不存在或无权操作");
        }
    }

    /**
     * categoryId 由客户端传。分类已是全站共享池，任何人发布/编辑时都可引用任意
     * 存在的分类；分类不存在与已软删都会在此被拦下（findById 自带 deleted = 0）。
     */
    private void assertCategoryExists(Long categoryId) {
        // add/update 在事务中持有频道行锁，与 CategoryService.delete 协调，防止产生孤儿文章。
        if (categoryMapper.findByIdForUpdate(categoryId) == null) {
            throw new BusinessException("分类不存在");
        }
    }

    /**
     * cover_img/summary 列是 NOT NULL DEFAULT ''，但 mapper 显式写了这两列，
     * 传 null 会撞非空约束（列默认值只在 INSERT 省略该列时才生效）
     */
    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    @Override
    public Article getById(Long id) {
        Article article = articleMapper.findById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        // 已发布 → 全员可看；草稿 → 仅作者本人，但管理员可纵览（含他人草稿）。
        // 无权限时与「不存在」回同一条模糊错误，防止用详情接口探测他人草稿是否存在
        // （与 assertCategoryExists 的模糊化处理同一思路）。
        boolean published = Integer.valueOf(1).equals(article.getState());
        Long userId = ThreadLocalUtils.getUserId();
        if (!published && !article.getUserId().equals(userId) && !isAdmin()) {
            throw new BusinessException("文章不存在");
        }
        articleMapper.incrementViewCount(id);
        // 顺带返回当前用户的互动状态：前端点赞/收藏按钮直接高亮，不用再发一次请求
        article.setLiked(userActionMapper.findId(userId, id,
                ActionTypes.TARGET_TYPE_ARTICLE, ActionTypes.ACTION_TYPE_LIKE) != null);
        article.setCollected(userActionMapper.findId(userId, id,
                ActionTypes.TARGET_TYPE_ARTICLE, ActionTypes.ACTION_TYPE_COLLECT) != null);
        return article;
    }

    /**
     * 编辑预填：返回完整文章（含 content），且不增加浏览量。
     * 权限与写操作一致（作者本人或管理员）；无权限时与「不存在」回同一条模糊错误。
     */
    @Override
    public Article getForEdit(Long id) {
        Article article = articleMapper.findById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        if (!article.getUserId().equals(ThreadLocalUtils.getUserId()) && !isAdmin()) {
            throw new BusinessException("文章不存在");
        }
        return article;
    }

    @Override
    public void delete(Long id) {
        int rows;
        if (isAdmin()) {
            // 管理员可删除任意文章
            rows = articleMapper.deleteByIdAny(id);
        } else {
            // 普通用户只能删自己的
            rows = articleMapper.deleteById(id, ThreadLocalUtils.getUserId());
        }
        // 0 行 = 文章不存在或不属于当前用户
        if (rows == 0) {
            throw new BusinessException("删除失败：文章不存在或无权操作");
        }
    }

    /**
     * 当前登录用户是否为管理员（user.role = 1）
     */
    private boolean isAdmin() {
        User me = userMapper.findById(ThreadLocalUtils.getUserId());
        return me != null && Integer.valueOf(1).equals(me.getRole());
    }

    /**
     * 条件分页查询
     * 先查总条数，再查当前页数据，封装成 PageBean 返回
     */
    @Override
    public PageBean<Article> list(Integer pageNum, Integer pageSize, Long categoryId, String state, String mode) {
        // 分页参数兜底：pageNum < 1 会算出负 offset 直接撞 SQL 语法错误；
        // pageSize 不设上限时客户端可以传超大值拖垮查询
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 5;
        }
        pageSize = Math.min(pageSize, 100);

        Long userId = ThreadLocalUtils.getUserId();
        Integer stateInt = parseState(state);
        String normalizedMode = mode == null || mode.isBlank() ? "latest" : mode.trim().toLowerCase(Locale.ROOT);
        if (!"latest".equals(normalizedMode) && !"recommend".equals(normalizedMode)) {
            throw new BusinessException("信息流模式仅支持 latest 或 recommend");
        }
        if ("recommend".equals(normalizedMode)) {
            if (stateInt != null && stateInt != 1) {
                throw new BusinessException("推荐模式仅支持已发布文章");
            }
            return recommend(userId, categoryId);
        }

        // 管理员纵览全站（userId 传 null 即不过滤作者，草稿也可见）；普通用户只看自己的
        Long queryUserId = isAdmin() ? null : userId;

        Long total = articleMapper.countByCondition(queryUserId, categoryId, stateInt);
        List<Article> items = List.of();
        if (total > 0) {
            int offset = (pageNum - 1) * pageSize;
            items = articleMapper.findByCondition(queryUserId, categoryId, stateInt, offset, pageSize);
        }
        return new PageBean<>(total, items);
    }

    /**
     * 推荐模式只对已发布文章取最新 100 条候选，重排后固定返回最多 30 条。
     * 不在 SQL 分页后做重排，避免高分文章落在后续页导致重复或漏项；pageNum/pageSize 不改变该窗口。
     */
    private PageBean<Article> recommend(Long userId, Long categoryId) {
        Long total = articleMapper.countByCondition(null, categoryId, 1);
        if (total == null || total == 0) {
            return new PageBean<>(0L, List.of());
        }

        List<Article> candidates = articleMapper.findByCondition(
                null, categoryId, 1, 0, RECOMMEND_CANDIDATE_LIMIT);
        UserProfile profile = userProfileService.get(userId);
        if (profile == null || profile.getSampleSize() == null
                || profile.getSampleSize() < MIN_PROFILE_SAMPLE_SIZE
                || profile.getInterests() == null || profile.getInterests().isEmpty()) {
            // 冷启动或样本太少时保留 mapper 的时间倒序，不用不可靠画像制造随机感。
            return new PageBean<>(total, candidates.stream().limit(RECOMMEND_RESULT_LIMIT).toList());
        }

        Map<Long, Double> categoryWeights = new HashMap<>();
        for (ProfileInterest interest : profile.getInterests()) {
            if (interest.getCategoryId() != null && interest.getWeight() != null) {
                categoryWeights.put(interest.getCategoryId(), interest.getWeight().doubleValue());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        candidates.sort(Comparator
                .comparingDouble((Article article) -> recommendationScore(article, categoryWeights, now)).reversed()
                .thenComparing(Article::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Article::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return new PageBean<>(total, candidates.stream().limit(RECOMMEND_RESULT_LIMIT).toList());
    }

    /** 规则重排：频道兴趣 × 七天半衰期时间衰减 × 平滑热度，24 小时内文章额外获得探索加权。 */
    private double recommendationScore(Article article, Map<Long, Double> categoryWeights, LocalDateTime now) {
        double categoryWeight = categoryWeights.getOrDefault(article.getCategoryId(), 0.0);
        LocalDateTime createdAt = article.getCreateTime() == null ? now : article.getCreateTime();
        double ageHours = Math.max(0.0, Duration.between(createdAt, now).toMinutes() / 60.0);
        double timeDecay = Math.exp(-Math.log(2.0) * ageHours / TIME_DECAY_HALF_LIFE_HOURS);
        double popularity = 1.0 + Math.log1p(Math.max(0, article.getViewCount() == null ? 0 : article.getViewCount()));
        double freshnessBoost = ageHours <= 24.0 ? NEW_ARTICLE_BOOST : 1.0;
        return (categoryWeight + UNSEEN_CATEGORY_WEIGHT) * timeDecay * popularity * freshnessBoost;
    }

    private Integer parseState(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        String normalized = state.trim();
        if ("草稿".equals(normalized) || "0".equals(normalized)) {
            return 0;
        }
        if ("已发布".equals(normalized) || "1".equals(normalized)) {
            return 1;
        }
        throw new BusinessException("文章状态仅支持 0/草稿 或 1/已发布");
    }
}
