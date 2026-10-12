package com.itheima.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/** Explicit admin-list projection: password/hash is not selected or serialized. */
@Data
public class AdminUserRow {
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private Integer role;
    private Integer userType;
    private Integer status;
    private Integer loginEnabled;
    private LocalDateTime createTime;
}
