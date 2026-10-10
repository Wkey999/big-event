package com.itheima.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启定时任务（阶段 B' 的每日画像刷新）。
 * 单独一个类而不是加在启动类上：启动类保持「只有 @SpringBootApplication + @MapperScan」的极简形态。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
