package com.itheima.service;

import com.itheima.mapper.FollowMapper;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.User;
import com.itheima.utils.ThreadLocalUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock private FollowMapper followMapper;
    @Mock private UserMapper userMapper;

    @AfterEach
    void clearUser() {
        ThreadLocalUtils.clear();
    }

    @Test
    void repeatedFollowDoesNotInsertAnotherRelation() {
        ThreadLocalUtils.setUserId(4L);
        FollowService service = new FollowService(followMapper, userMapper);
        when(userMapper.findById(8L)).thenReturn(new User());
        when(followMapper.countRelation(4L, 8L)).thenReturn(1);
        when(followMapper.countFollowers(8L)).thenReturn(3L);
        when(followMapper.countFollowees(8L)).thenReturn(2L);

        var state = service.follow(8L, true);

        assertThat(state.isFollowed()).isTrue();
        assertThat(state.getFollowerCount()).isEqualTo(3L);
        assertThat(state.getFolloweeCount()).isEqualTo(2L);
        verify(followMapper, never()).insert(4L, 8L);
    }

    @Test
    void repeatedUnfollowCannotDriveCountsBelowActualRelations() {
        ThreadLocalUtils.setUserId(4L);
        FollowService service = new FollowService(followMapper, userMapper);
        when(userMapper.findById(8L)).thenReturn(new User());
        when(followMapper.countFollowers(8L)).thenReturn(0L);
        when(followMapper.countFollowees(8L)).thenReturn(0L);

        var state = service.follow(8L, false);

        assertThat(state.isFollowed()).isFalse();
        assertThat(state.getFollowerCount()).isZero();
        assertThat(state.getFolloweeCount()).isZero();
        verify(followMapper).delete(4L, 8L);
    }
}
