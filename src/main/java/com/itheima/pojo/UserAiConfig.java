package com.itheima.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/** Transient request credentials only. Never persist or return the API key. */
@Data
public class UserAiConfig {
    @NotBlank(message="请选择 AI 服务商")
    @Pattern(regexp="deepseek|openai|siliconflow",message="请选择支持的 AI 服务商")
    private String provider;

    @NotBlank(message="请填写模型名称")
    @Size(max=120,message="模型名称最长 120 个字符")
    @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:/-]*",message="模型名称格式不正确")
    private String model;

    @NotBlank(message="请先配置你自己的 API Key")
    @Size(max=512,message="API Key 长度不正确")
    @Pattern(regexp="[\\x21-\\x7E]+",message="API Key 不能包含空格或换行")
    @JsonProperty(access=JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String apiKey;
}
