package com.itheima.pojo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体类 - 对应数据库 user 表
 */
@Data
public class User {
    /** 主键ID */
    private Long id;
    /** 用户名（登录账号，唯一） */
    private String username;
    /** 密码（BCrypt加密存储） */
    private String password;
    /** 昵称（默认与用户名相同） */
    private String nickname;
    /** 邮箱 */
    private String email;
    /** 头像URL */
    private String avatar;
    /** 角色：0-普通用户 1-管理员 */
    private Integer role;
    /** 状态：0-禁用 1-正常 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 逻辑删除：0-未删 1-已删 */
    private Integer deleted;
}
