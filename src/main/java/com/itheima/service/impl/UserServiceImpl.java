package com.itheima.service.impl;

import com.itheima.mapper.UserMapper;
import com.itheima.pojo.User;
import com.itheima.pojo.UserLoginDTO;
import com.itheima.pojo.UserRegisterDTO;
import com.itheima.pojo.UserUpdatePwdDTO;
import com.itheima.service.UserService;
import com.itheima.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户注册
     *
     * @param dto 用户注册信息，包含用户名、密码和确认密码
     */
    @Override
    public void register(UserRegisterDTO dto) {
        // 校验两次密码是否一致
        if (!dto.getPassword().equals(dto.getRePassword())) {
            throw new RuntimeException("两次密码不一致");
        }
        // 校验用户名是否已存在
        User existing = userMapper.findByUsername(dto.getUsername());
        if (existing != null) {
            throw new RuntimeException("用户名已存在");
        }
        // 构建用户对象，密码加密后持久化
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getUsername());
        userMapper.insert(user);
    }

    @Override
    public String login(UserLoginDTO dto) {
        User user = userMapper.findByUsername(dto.getUsername());
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }
        return jwtUtils.generateToken(user.getId(), user.getUsername());
    }

    @Override
    public User getUserInfo(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setPassword(null);
        return user;
    }

    @Override
    public void updateUserInfo(User user) {
        userMapper.update(user);
    }

    @Override
    public void updatePassword(Long userId, UserUpdatePwdDTO dto) {
        User user = userMapper.findById(userId);
        if (user == null || !passwordEncoder.matches(dto.getOld_pwd(), user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(dto.getNew_pwd()));
    }
}
