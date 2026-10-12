package com.itheima.pojo;

import com.itheima.validation.InValues;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUserRoleDTO {
    @NotNull(message = "用户角色不能为空")
    @InValues(values = {0, 1}, message = "用户角色只能是0或1")
    private Integer role;
}
