package com.itheima.service;

import com.itheima.pojo.UserProfile;

import java.util.List;

public interface UserProfileService {

    /** 读取画像；从未生成过（冷启动）返回 null，由调用方决定兜底策略 */
    UserProfile get(Long userId);

    /**
     * 依据近 7 天行为重新生成规则画像（阶段 B'，零 LLM）。
     * 没有任何行为时会清空旧画像并返回 null——兴趣会漂移，过期画像比没有画像更危险。
     */
    UserProfile generate(Long userId);

    /** 近 N 天有过行为的用户，供定时任务批量刷新（只处理活跃用户） */
    List<Long> activeUserIds(int days);
}
