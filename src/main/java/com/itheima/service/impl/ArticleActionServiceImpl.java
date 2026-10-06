package com.itheima.service.impl;

import com.itheima.exception.BusinessException;
import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.UserActionMapper;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.Article;
import com.itheima.pojo.ArticleActionDTO;
import com.itheima.pojo.User;
import com.itheima.service.ArticleActionService;
import com.itheima.utils.ActionTypes;
import com.itheima.utils.ThreadLocalUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArticleActionServiceImpl implements ArticleActionService {

    private final UserActionMapper userActionMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;

    /**
     * 关系表 user_action 与文章冗余计数必须一起成功或一起失败：
     * 中间失败会让 like_count 与实际关系数量长期漂移，而且再也对不上。
     * （这也是本项目第一个真正需要事务的地方——顺带把「零 @Transactional」的欠账补上）
     */
    @Override
    @Transactional
    public void act(ArticleActionDTO dto) {
        Long userId = ThreadLocalUtils.getUserId();
        Article article = articleMapper.findById(dto.getArticleId());
        // 与阅读权限同一套规则：只能对看得见的文章互动，
        // 否则这个接口能给别人草稿刷计数、也能用来探测草稿是否存在
        if (article == null || !visibleTo(article, userId)) {
            throw new BusinessException("文章不存在");
        }

        int actionType = dto.getActionType();
        boolean doing = Integer.valueOf(1).equals(dto.getOp());

        if (doing) {
            if (userActionMapper.findId(userId, article.getId(), ActionTypes.TARGET_TYPE_ARTICLE, actionType) != null) {
                return;   // 已点赞/已收藏：幂等，不再加计数
            }
            try {
                userActionMapper.insert(userId, article.getId(), ActionTypes.TARGET_TYPE_ARTICLE, actionType);
            } catch (DuplicateKeyException e) {
                // 并发下唯一索引 uk_user_target_action 兜底：说明另一请求已插入，幂等返回。
                // 与注册接口的「先查后插 + 唯一索引兜底」是同一个模式，这是第二处使用。
                return;
            }
            changeCounter(article.getId(), actionType, 1);
        } else {
            int rows = userActionMapper.delete(userId, article.getId(), ActionTypes.TARGET_TYPE_ARTICLE, actionType);
            if (rows == 0) {
                return;   // 本来就没点赞/收藏：幂等
            }
            changeCounter(article.getId(), actionType, -1);
        }
    }

    /** 冗余计数增减（收藏走 collect_count，与 like_count 对称） */
    private void changeCounter(Long articleId, int actionType, int delta) {
        if (actionType == ActionTypes.ACTION_TYPE_LIKE) {
            if (delta > 0) {
                articleMapper.incrementLikeCount(articleId);
            } else {
                articleMapper.decrementLikeCount(articleId);
            }
            return;
        }
        if (actionType == ActionTypes.ACTION_TYPE_COLLECT) {
            if (delta > 0) {
                articleMapper.incrementCollectCount(articleId);
            } else {
                articleMapper.decrementCollectCount(articleId);
            }
        }
    }

    /**
     * 已发布任何人都能看；草稿只有作者本人和管理员能看。
     * 与 BrowseLogServiceImpl.visibleTo、ArticleServiceImpl.getById 是同一套规则——
     * 三处重复说明它该被抽成一个组件，等第四个调用点出现时再提取（现在抽反而多一层跳转）。
     */
    private boolean visibleTo(Article article, Long userId) {
        if (Integer.valueOf(1).equals(article.getState())) {
            return true;
        }
        if (article.getUserId().equals(userId)) {
            return true;
        }
        User me = userMapper.findById(userId);
        return me != null && Integer.valueOf(1).equals(me.getRole());
    }
}
