package com.itheima.service;

import com.itheima.exception.BusinessException;
import com.itheima.mapper.FollowMapper;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.FollowState;
import com.itheima.pojo.User;
import com.itheima.utils.ThreadLocalUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;

    @Transactional
    public FollowState follow(Long targetId, boolean follow) {
        Long followerId = ThreadLocalUtils.getUserId();
        if (followerId == null) {
            throw new BusinessException("请先登录");
        }
        if (followerId.equals(targetId)) {
            throw new BusinessException("不能关注自己");
        }
        User target = userMapper.findById(targetId);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }
        if (follow) {
            if (followMapper.countRelation(followerId, targetId) == 0) {
                try {
                    followMapper.insert(followerId, targetId);
                } catch (DuplicateKeyException ignored) {
                    // Unique key makes concurrent follow requests idempotent.
                }
            }
        } else {
            followMapper.delete(followerId, targetId);
        }
        return state(targetId, followerId);
    }

    public FollowState state(Long targetId, Long currentUserId) {
        if (userMapper.findById(targetId) == null) {
            throw new BusinessException("用户不存在");
        }
        boolean followed = currentUserId != null && !currentUserId.equals(targetId)
                && followMapper.countRelation(currentUserId, targetId) > 0;
        return new FollowState(followed, followMapper.countFollowers(targetId), followMapper.countFollowees(targetId));
    }
}
