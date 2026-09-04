package com.itheima.pojo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 更新用户信息请求参数 - 带参数校验
 */
@Data
public class UserUpdateDTO {
    /** 昵称：不能为空，1-10个字符 */
    @NotBlank(message = "昵称不能为空")
    @Pattern(regexp = "^\\S{1,10}$", message = "昵称必须是1-10位非空字符")
    private String nickname;

    /** 邮箱：不能为空，必须符合邮箱格式 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 头像URL：可选，上传头像后传入返回的URL */
    private String avatar;
}
