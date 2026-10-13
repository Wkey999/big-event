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
import com.itheima.pojo.User;
import com.itheima.utils.ThreadLocalUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentMapper commentMapper;
    @Mock private ArticleMapper articleMapper;
    @Mock private CommunityMapper communityMapper;
    @Mock private UserActionMapper userActionMapper;
    @Mock private UserMapper userMapper;

    @AfterEach
    void clearUser() {
        ThreadLocalUtils.clear();
    }

    @Test
    void addingCommentAtomicallyUpdatesArticleCount() {
        ThreadLocalUtils.setUserId(4L);
        CommentService service = new CommentService(commentMapper, articleMapper, communityMapper,
                userActionMapper, userMapper);
        Article article = new Article();
        article.setId(8L);
        article.setState(1);
        when(communityMapper.findArticleForUpdate(8L)).thenReturn(article);
        when(commentMapper.insert(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setId(30L);
            return 1;
        });
        when(articleMapper.incrementCommentCount(8L)).thenReturn(1);
        Comment saved = new Comment();
        saved.setId(30L);
        saved.setUserId(4L);
        saved.setArticleId(8L);
        saved.setContent("hello");
        saved.setLikeCount(0);
        saved.setReplyCount(0);
        saved.setCreateTime(LocalDateTime.of(2026, 10, 14, 1, 0));
        when(commentMapper.findVisibleById(30L)).thenReturn(saved);
        User author = new User();
        author.setNickname("writer");
        author.setAvatar("");
        when(userMapper.findById(4L)).thenReturn(author);
        CommentDTO dto = new CommentDTO();
        dto.setContent("hello");

        var result = service.add(8L, dto);

        verify(commentMapper).insert(any(Comment.class));
        verify(articleMapper).incrementCommentCount(8L);
        org.assertj.core.api.Assertions.assertThat(result.getId()).isEqualTo(30L);
        org.assertj.core.api.Assertions.assertThat(result.getNickname()).isEqualTo("writer");
    }

    @Test
    void refusesRepliesToAnotherArticleRoot() {
        ThreadLocalUtils.setUserId(4L);
        CommentService service = new CommentService(commentMapper, articleMapper, communityMapper,
                userActionMapper, userMapper);
        Article article = new Article();
        article.setId(8L);
        article.setState(1);
        when(communityMapper.findArticleForUpdate(8L)).thenReturn(article);
        when(commentMapper.countValidRoot(99L, 8L)).thenReturn(0);
        CommentDTO dto = new CommentDTO();
        dto.setContent("reply");
        dto.setRootId(99L);

        assertThatThrownBy(() -> service.add(8L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("回复所属楼层不存在");
        verify(commentMapper, never()).insert(any(Comment.class));
        verify(articleMapper, never()).incrementCommentCount(8L);
    }

    @Test
    void draftArticleCannotBeCommentedOn() {
        ThreadLocalUtils.setUserId(4L);
        CommentService service = new CommentService(commentMapper, articleMapper, communityMapper,
                userActionMapper, userMapper);
        Article draft = new Article();
        draft.setId(8L);
        draft.setState(0);
        when(communityMapper.findArticleForUpdate(8L)).thenReturn(draft);
        CommentDTO dto = new CommentDTO();
        dto.setContent("hello");

        assertThatThrownBy(() -> service.add(8L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文章不存在或暂不可评论");
        verify(commentMapper, never()).insert(any(Comment.class));
    }

    @Test
    void repeatedCommentLikeDoesNotIncrementCounterAgain() {
        ThreadLocalUtils.setUserId(4L);
        CommentService service = new CommentService(commentMapper, articleMapper, communityMapper,
                userActionMapper, userMapper);
        Comment comment = new Comment();
        comment.setId(30L);
        when(commentMapper.findVisibleById(30L)).thenReturn(comment);
        when(userActionMapper.findId(4L, 30L, 2, 1)).thenReturn(9L);

        service.like(30L, true);

        verify(userActionMapper, never()).insert(4L, 30L, 2, 1);
        verify(commentMapper, never()).incrementLikeCount(30L);
    }
}
