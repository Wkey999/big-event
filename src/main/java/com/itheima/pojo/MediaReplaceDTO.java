package com.itheima.pojo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class MediaReplaceDTO {
    @Valid
    @Size(max = 10, message = "媒体最多10项")
    private List<@Valid PostMediaDTO> media;
}
