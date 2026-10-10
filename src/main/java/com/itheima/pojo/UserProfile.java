package com.itheima.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户偏好画像（阶段 B' 规则画像；阶段 B 会用 LLM 归纳覆盖同一张表）
 *
 * 说明：active_hours 在库里是逗号分隔字符串（省掉 JSON TypeHandler），
 * 对外的 activeHourList 才是数组，所以把原始字段用 @JsonIgnore 藏起来。
 */
@Data
public class UserProfile {
    /** 用户ID */
    private Long userId;
    /** 活跃小时原始值（CSV），仅持久化用，不对外暴露 */
    @JsonIgnore
    private String activeHours;
    /** 画像来源：rule-规则统计 llm-大模型归纳 */
    private String source;
    /** 参与统计的行为条数（置信度参考：数据太少时画像不可信） */
    private Integer sampleSize;
    /** 生成时间 */
    private LocalDateTime generatedAt;
    /** 频道兴趣，按权重降序 */
    private List<ProfileInterest> interests = new ArrayList<>();

    /** 活跃时段（小时，降序前几个），从 CSV 解析 */
    public List<Integer> getActiveHourList() {
        List<Integer> hours = new ArrayList<>();
        if (activeHours == null || activeHours.isEmpty()) {
            return hours;
        }
        for (String part : activeHours.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                hours.add(Integer.valueOf(trimmed));
            }
        }
        return hours;
    }
}
