package com.itheima.service;

import com.itheima.pojo.User;
import com.itheima.pojo.UserLoginDTO;
import com.itheima.pojo.UserRegisterDTO;
import com.itheima.pojo.UserUpdatePwdDTO;

public interface UserService {

    void register(UserRegisterDTO dto);

    String login(UserLoginDTO dto);

    User getUserInfo(Long userId);

    void updateUserInfo(User user);

    void updatePassword(Long userId, UserUpdatePwdDTO dto);
}
