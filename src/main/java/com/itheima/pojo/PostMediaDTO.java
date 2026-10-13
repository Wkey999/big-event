package com.itheima.pojo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostMediaDTO {
    @NotBlank(message = "媒体地址不能为空")
    @Size(max = 255, message = "媒体地址最长255个字符")
    private String url;

    @Min(value = 1, message = "媒体类型仅支持图片或视频")
    @Max(value = 2, message = "媒体类型仅支持图片或视频")
    private Integer mediaType = 1;

    @PositiveOrZero(message = "媒体宽度不合法")
    private Integer width;

    @PositiveOrZero(message = "媒体高度不合法")
    private Integer height;
}
