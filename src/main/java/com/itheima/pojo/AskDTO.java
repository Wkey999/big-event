package com.itheima.pojo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AskDTO {
    @NotBlank(message = "问题不能为空")
    @Size(max = 200, message = "问题最长 200 个字符")
    private String question;
}
