package com.itheima.service.agent;

import com.itheima.config.AiTrialProperties;
import com.itheima.exception.BusinessException;
import com.itheima.mapper.AiTrialQuotaMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.util.HexFormat;

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
    public void reserve(Long userId,String clientIp) {
        requireUser(userId);
        String ip=ipScope(clientIp);
        LocalDate date=LocalDate.now(clock);
        String user="user:"+userId;
        mapper.ensureRow(date, "site");
        if(mapper.lockCount(date, "site")>=properties.getDailySiteLimit())
            throw new BusinessException("今日全站免费体验额度已用完，请使用自己的 API 或明天再试");
        mapper.ensureRow(date,ip);
        if(mapper.lockCount(date,ip)>=properties.getDailyIpLimit())
            throw new BusinessException("当前网络今日免费体验次数已用完，请使用自己的 API 或明天再试");
        mapper.ensureRow(date, user);
        if(mapper.lockCount(date, user)>=properties.getDailyUserLimit())
            throw new BusinessException("你今天的免费次数已用完，请使用自己的 API 或明天再试");
        mapper.increment(date,"site");
        mapper.increment(date,ip);
        mapper.increment(date,user);
    }

    public Quota status(Long userId,String clientIp) {
        requireUser(userId);
        LocalDate date=LocalDate.now(clock);
        return new Quota(properties.getDailyUserLimit(), remaining(properties.getDailyUserLimit(), mapper.count(date,"user:"+userId)),
                remaining(properties.getDailySiteLimit(), mapper.count(date,"site")),
                date.plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime().toString(),
                properties.getDailyIpLimit(),remaining(properties.getDailyIpLimit(),mapper.count(date,ipScope(clientIp))));
    }
    private static int remaining(int limit,Integer used) { return Math.max(0,limit-(used==null?0:used)); }
    private static void requireUser(Long userId) {
        if(userId==null || userId<=0) throw new BusinessException("请先登录后使用 AI 搜索");
    }
    static String ipScope(String clientIp) {
        if(clientIp==null || clientIp.length()>45 || !clientIp.matches("[0-9A-Fa-f:.]+")
                || (!clientIp.contains(":") && !clientIp.matches("\\d{1,3}(\\.\\d{1,3}){3}")))
            throw new BusinessException("无法识别请求来源，请稍后重试");
        try {
            byte[] bytes=InetAddress.getByName(clientIp).getAddress();
            // Group IPv6 privacy addresses by /64 so rotating within one subnet cannot reset quota.
            if(bytes.length==16) java.util.Arrays.fill(bytes,8,16,(byte)0);
            return "ip:"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0,48);
        } catch(Exception e) { throw new BusinessException("无法识别请求来源，请稍后重试"); }
    }
    public record Quota(int dailyLimit,int remaining,int siteRemaining,String resetsAt,int ipDailyLimit,int ipRemaining) {}
}
