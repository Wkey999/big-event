package com.itheima.pojo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import lombok.Data;

@Data
public class AskDTO {
    @NotBlank(message = "问题不能为空")
    @Size(max = 200, message = "问题最长 200 个字符")
    private String question;
    @NotBlank
    @Pattern(regexp="platform|custom", message="请选择免费体验或自己的 API")
    private String mode = "platform";
    @Valid
    private UserAiConfig aiConfig;
}
