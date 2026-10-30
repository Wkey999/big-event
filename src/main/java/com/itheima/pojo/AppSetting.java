package com.itheima.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/** 运行时功能开关（app_setting 表）。value 为空表示“跟随配置文件默认值”。 */
@Data
public class AppSetting {
    private String settingKey;
    private String settingValue;
    private Long updatedBy;
    private LocalDateTime updateTime;
}
