package com.itheima.service.agent;

import com.itheima.exception.BusinessException;
import com.itheima.pojo.UserProfile;
import com.itheima.service.UserProfileService;
import com.itheima.utils.ThreadLocalUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** Returns only the authenticated caller's profile; the model cannot choose a user ID. */
@Component
@RequiredArgsConstructor
public class ProfileTools {

    private final UserProfileService userProfileService;

    @Tool(name = "get_my_preference_profile", description = "Get the authenticated user's own category-level interest profile. Takes no user ID.")
    public UserProfile getMyProfile() {
        Long userId = ThreadLocalUtils.getUserId();
        if (userId == null) {
            throw new BusinessException("未登录");
        }
        return userProfileService.get(userId);
    }
}
