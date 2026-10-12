package com.itheima.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    /** 用户类型：0-人类 1-机器作者；仅后台使用，不改变旧接口响应。 */
    @JsonIgnore
    private Integer userType;
    /** 状态：0-禁用 1-正常 */
    private Integer status;
    /** 登录开关：0-不可登录 1-可登录；仅后台使用。 */
    @JsonIgnore
    private Integer loginEnabled;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 逻辑删除：0-未删 1-已删 */
    private Integer deleted;
}
