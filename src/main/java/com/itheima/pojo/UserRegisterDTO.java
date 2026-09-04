package com.itheima.pojo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 用户注册请求参数
 */
@Data
public class UserRegisterDTO {
    /** 用户名：3-20位字母或数字 */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{3,20}$", message = "用户名必须是3-20位字母或数字")
    private String username;

    /** 密码：6-20位非空字符 */
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^\\S{6,20}$", message = "密码必须是6-20位非空字符")
    private String password;

    /** 确认密码：必须与password一致 */
    @NotBlank(message = "确认密码不能为空")
    private String rePassword;
}
