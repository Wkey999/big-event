package com.itheima.pojo;

import com.itheima.validation.InValues;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUserStatusDTO {
    @NotNull(message = "用户状态不能为空")
    @InValues(values = {0, 1}, message = "用户状态只能是0或1")
    private Integer status;
}
