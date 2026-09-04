package com.itheima.pojo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 修改密码请求参数
 *
 * 之前 controller 直接收 Map<String,String>，零校验：新密码传空串也能 BCrypt 落库，
 * 而登录接口密码是 @NotBlank，改完账号就永远登录不进去。
 * 字段名保留下划线命名，维持前端既有契约（body key 就是 old_pwd / new_pwd）。
 */
@Data
public class UserUpdatePwdDTO {
    /** 原密码：必填 */
    @NotBlank(message = "原密码不能为空")
    private String old_pwd;

    /** 新密码：必填，6-20位非空字符，与注册规则一致 */
    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^\\S{6,20}$", message = "新密码必须是6-20位非空字符")
    private String new_pwd;
}
