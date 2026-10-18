package com.itheima.service.agent;

import com.itheima.config.AiTrialProperties;
import com.itheima.exception.BusinessException;
import com.itheima.mapper.AiTrialQuotaMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
public class AiTrialQuotaService {
    private final AiTrialQuotaMapper mapper;
    private final AiTrialProperties properties;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AiTrialQuotaService(AiTrialQuotaMapper mapper, AiTrialProperties properties) {
        this(mapper, properties, Clock.system(ZoneId.of("Asia/Shanghai")));
    }
    AiTrialQuotaService(AiTrialQuotaMapper mapper, AiTrialProperties properties, Clock clock) {
        this.mapper=mapper; this.properties=properties; this.clock=clock;
    }

    // Commit before invoking the external model. Lock site first, then user on every JVM.
    @Transactional
    public void reserve(Long userId) {
        requireUser(userId);
        LocalDate date=LocalDate.now(clock);
        String user="user:"+userId;
        mapper.ensureRow(date, "site");
        if(mapper.lockCount(date, "site")>=properties.getDailySiteLimit())
            throw new BusinessException("今日全站免费体验额度已用完，请使用自己的 API 或明天再试");
        mapper.ensureRow(date, user);
        if(mapper.lockCount(date, user)>=properties.getDailyUserLimit())
            throw new BusinessException("你今天的免费次数已用完，请使用自己的 API 或明天再试");
        mapper.increment(date,"site");
        mapper.increment(date,user);
    }

    public Quota status(Long userId) {
        requireUser(userId);
        LocalDate date=LocalDate.now(clock);
        return new Quota(properties.getDailyUserLimit(), remaining(properties.getDailyUserLimit(), mapper.count(date,"user:"+userId)),
                remaining(properties.getDailySiteLimit(), mapper.count(date,"site")),
                date.plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime().toString());
    }
    private static int remaining(int limit,Integer used) { return Math.max(0,limit-(used==null?0:used)); }
    private static void requireUser(Long userId) {
        if(userId==null || userId<=0) throw new BusinessException("请先登录后使用 AI 搜索");
    }
    public record Quota(int dailyLimit,int remaining,int siteRemaining,String resetsAt) {}
}
