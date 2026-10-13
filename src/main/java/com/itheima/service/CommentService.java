package com.itheima.service;

import com.itheima.exception.BusinessException;
import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.CommentMapper;
import com.itheima.mapper.CommunityMapper;
import com.itheima.mapper.UserActionMapper;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.Article;
import com.itheima.pojo.Comment;
import com.itheima.pojo.CommentDTO;
import com.itheima.pojo.CommentReplyView;
import com.itheima.pojo.CommentView;
import com.itheima.pojo.PageBean;
import com.itheima.pojo.User;
import com.itheima.utils.ActionTypes;
import com.itheima.utils.ThreadLocalUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int ROOT_PREVIEW_SIZE = 2;

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;
    private final CommunityMapper communityMapper;
    private final UserActionMapper userActionMapper;
    private final UserMapper userMapper;

    public PageBean<CommentView> listRoots(Long articleId, Integer requestedPage, Integer requestedSize) {
        Long userId = ThreadLocalUtils.getUserId();
        Article article = communityMapper.findArticle(articleId);
        assertArticleReadable(article, userId);
        int page = requestedPage == null || requestedPage < 1 ? 1 : requestedPage;
        int size = requestedSize == null || requestedSize < 1 ? 20 : Math.min(MAX_PAGE_SIZE, requestedSize);
        long total = commentMapper.countRoots(articleId);
        if (total == 0) {
            return new PageBean<>(0L, List.of());
        }
        int offset = (int) Math.min((long) (page - 1) * size, Integer.MAX_VALUE);
        List<CommentView> roots = commentMapper.listRoots(articleId, offset, size);
        for (CommentView root : roots) {
            root.setLiked(isLiked(userId, root.getId()));
            List<CommentReplyView> replies = commentMapper.firstReplies(root.getId(), ROOT_PREVIEW_SIZE);
            for (CommentReplyView reply : replies) {
                reply.setLiked(isLiked(userId, reply.getId()));
            }
            root.setReplies(replies);
        }
        return new PageBean<>(total, roots);
    }

    public PageBean<CommentReplyView> listReplies(Long rootId, Integer requestedPage, Integer requestedSize) {
        Long userId = ThreadLocalUtils.getUserId();
        Comment root = commentMapper.findVisibleById(rootId);
        if (root == null || root.getRootId() != null) {
            throw new BusinessException("评论楼层不存在");
        }
        Article article = communityMapper.findArticle(root.getArticleId());
        assertArticleReadable(article, userId);
        int page = requestedPage == null || requestedPage < 1 ? 1 : requestedPage;
        int size = requestedSize == null || requestedSize < 1 ? 20 : Math.min(MAX_PAGE_SIZE, requestedSize);
        long total = commentMapper.countReplies(rootId);
        if (total == 0) {
            return new PageBean<>(0L, List.of());
        }
        int offset = (int) Math.min((long) (page - 1) * size, Integer.MAX_VALUE);
        List<CommentReplyView> replies = commentMapper.listReplies(rootId, offset, size);
        for (CommentReplyView reply : replies) {
            reply.setLiked(isLiked(userId, reply.getId()));
        }
        return new PageBean<>(total, replies);
    }

    @Transactional
    public CommentView add(Long articleId, CommentDTO dto) {
        Long userId = requireUserId();
        Article article = communityMapper.findArticleForUpdate(articleId);
        if (article == null || !Integer.valueOf(1).equals(article.getState())) {
            throw new BusinessException("文章不存在或暂不可评论");
        }
        if (dto.getRootId() == null) {
            if (dto.getParentId() != null) {
                throw new BusinessException("回复必须指定所属楼层");
            }
        } else {
            if (commentMapper.countValidRoot(dto.getRootId(), articleId) == 0) {
                throw new BusinessException("回复所属楼层不存在");
            }
            if (dto.getParentId() != null
                    && commentMapper.countValidParent(dto.getParentId(), articleId, dto.getRootId()) == 0) {
                throw new BusinessException("回复对象不属于该楼层");
            }
        }

        Comment comment = new Comment();
        comment.setArticleId(articleId);
        comment.setUserId(userId);
        comment.setRootId(dto.getRootId());
        comment.setParentId(dto.getRootId() == null ? null
                : dto.getParentId() == null ? dto.getRootId() : dto.getParentId());
        comment.setContent(dto.getContent());
        commentMapper.insert(comment);
        if (dto.getRootId() != null && commentMapper.incrementReplyCount(dto.getRootId()) != 1) {
            throw new BusinessException("回复所属楼层不存在");
        }
        if (articleMapper.incrementCommentCount(articleId) != 1) {
            throw new BusinessException("文章不存在或暂不可评论");
        }
        return toView(commentMapper.findVisibleById(comment.getId()));
    }

    @Transactional
    public void delete(Long commentId) {
        Long userId = requireUserId();
        Comment initial = commentMapper.findVisibleById(commentId);
        if (initial == null) {
            throw new BusinessException("评论不存在");
        }
        communityMapper.findArticleForUpdate(initial.getArticleId());
        Comment comment = commentMapper.findByIdForUpdate(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        if (!comment.getUserId().equals(userId) && !isAdmin(userId)) {
            throw new BusinessException("无权删除该评论");
        }

        int deletedCount;
        if (comment.getRootId() == null) {
            int replies = commentMapper.countUndeletedReplies(commentId);
            commentMapper.softDeleteReplies(commentId);
            deletedCount = replies + commentMapper.softDeleteRoot(commentId);
        } else {
            deletedCount = commentMapper.softDeleteReply(commentId);
            commentMapper.decrementReplyCount(comment.getRootId());
        }
        if (deletedCount > 0 && articleMapper.decrementCommentCount(comment.getArticleId(), deletedCount) != 1) {
            throw new IllegalStateException("article.comment_count update failed");
        }
    }

    @Transactional
    public void like(Long commentId, boolean like) {
        Long userId = requireUserId();
        Comment comment = commentMapper.findVisibleById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        int targetType = ActionTypes.TARGET_TYPE_COMMENT;
        int actionType = ActionTypes.ACTION_TYPE_LIKE;
        if (like) {
            if (userActionMapper.findId(userId, commentId, targetType, actionType) != null) {
                return;
            }
            try {
                userActionMapper.insert(userId, commentId, targetType, actionType);
            } catch (DuplicateKeyException duplicate) {
                return;
            }
            if (commentMapper.incrementLikeCount(commentId) != 1) {
                throw new BusinessException("评论不存在");
            }
            return;
        }
        if (userActionMapper.delete(userId, commentId, targetType, actionType) > 0
                && commentMapper.decrementLikeCount(commentId) != 1) {
            throw new IllegalStateException("comment.like_count update failed");
        }
    }

    private CommentView toView(Comment comment) {
        User author = userMapper.findById(comment.getUserId());
        CommentView view = new CommentView();
        view.setId(comment.getId());
        view.setUserId(comment.getUserId());
        view.setNickname(author == null ? "" : author.getNickname());
        view.setAvatar(author == null ? "" : author.getAvatar());
        view.setContent(comment.getContent());
        view.setLikeCount(0);
        view.setLiked(false);
        view.setReplyCount(comment.getReplyCount());
        view.setCreateTime(comment.getCreateTime());
        view.setReplies(List.of());
        return view;
    }

    private boolean isLiked(Long userId, Long commentId) {
        return userId != null && userActionMapper.findId(userId, commentId,
                ActionTypes.TARGET_TYPE_COMMENT, ActionTypes.ACTION_TYPE_LIKE) != null;
    }

    private void assertArticleReadable(Article article, Long userId) {
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        if (Integer.valueOf(1).equals(article.getState()) || (userId != null && isOwnerOrAdmin(article, userId))) {
            return;
        }
        throw new BusinessException("文章不存在");
    }

    private boolean isOwnerOrAdmin(Article article, Long userId) {
        return article.getUserId().equals(userId) || isAdmin(userId);
    }

    private boolean isAdmin(Long userId) {
        User user = userId == null ? null : userMapper.findById(userId);
        return user != null && Integer.valueOf(1).equals(user.getRole());
    }

    private Long requireUserId() {
        Long userId = ThreadLocalUtils.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        return userId;
    }
}
