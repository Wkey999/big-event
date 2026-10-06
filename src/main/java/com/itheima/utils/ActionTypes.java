package com.itheima.utils;

/**
 * user_action 表的取值约定（建表脚本里有注释，这里给代码一处引用点，避免散落魔法数字）
 */
public final class ActionTypes {

    private ActionTypes() {
    }

    /** target_type：1-文章 2-评论 3-用户 */
    public static final int TARGET_TYPE_ARTICLE = 1;

    /** action_type：1-点赞 */
    public static final int ACTION_TYPE_LIKE = 1;

    /** action_type：2-收藏 */
    public static final int ACTION_TYPE_COLLECT = 2;
}
