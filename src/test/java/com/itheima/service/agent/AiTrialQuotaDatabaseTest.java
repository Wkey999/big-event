package com.itheima.service.agent;

import com.itheima.config.AiTrialProperties;
import com.itheima.exception.BusinessException;
import com.itheima.mapper.AiTrialQuotaMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.time.*;
import java.util.ArrayList;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in, real MySQL 5.7 concurrency test. Only touches reserved test dates. No model requests. */
@EnabledIfEnvironmentVariable(named="AI_QUOTA_DB_TEST",matches="true")
class AiTrialQuotaDatabaseTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private AiTrialQuotaService service;
    private AiTrialProperties properties;
    private static final String TEST_DATE="2099-10-19";

    @BeforeEach void setup() {
        context=new AnnotationConfigApplicationContext(Config.class);
        jdbc=new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.update("DELETE FROM ai_trial_daily_usage WHERE usage_date=?",TEST_DATE);
        service=context.getBean(AiTrialQuotaService.class); properties=context.getBean(AiTrialProperties.class);
    }
    @AfterEach void cleanup() {
        if(jdbc!=null) jdbc.update("DELETE FROM ai_trial_daily_usage WHERE usage_date=?",TEST_DATE);
        if(context!=null) context.close();
    }
    @Test void concurrentRequestsCannotExceedUserAllowanceAndNewServiceSeesSameUsage() throws Exception {
        assertThat(runRequests(false)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT used_count FROM ai_trial_daily_usage WHERE usage_date=? AND scope_id='site'",Integer.class,TEST_DATE)).isEqualTo(3);
        var reloaded=new AiTrialQuotaService(context.getBean(AiTrialQuotaMapper.class),properties,Config.CLOCK);
        assertThat(reloaded.status(900000000001L).remaining()).isZero();
    }
    @Test void concurrentDifferentAccountsCannotExceedSiteAllowance() throws Exception {
        properties.setDailySiteLimit(5);
        assertThat(runRequests(true)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT SUM(used_count) FROM ai_trial_daily_usage WHERE usage_date=? AND scope_id LIKE 'user:%'",Integer.class,TEST_DATE)).isEqualTo(5);
    }
    private int runRequests(boolean differentUsers) throws Exception {
        try(var executor=Executors.newFixedThreadPool(12)) {
            var ready=new CountDownLatch(12); var go=new CountDownLatch(1);
            var futures=new ArrayList<Future<Boolean>>();
            for(int i=0;i<12;i++) {
                final long user=900000000001L+(differentUsers?i:0);
                futures.add(executor.submit(() -> {
                    ready.countDown(); go.await();
                    try { service.reserve(user); return true; } catch(BusinessException expected) { return false; }
                }));
            }
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); go.countDown();
            int successes=0; for(var future:futures) if(future.get(20,TimeUnit.SECONDS)) successes++;
            return successes;
        }
    }
    @Configuration
    @EnableTransactionManagement(proxyTargetClass=true)
    static class Config {
        static final Clock CLOCK=Clock.fixed(Instant.parse("2099-10-19T01:00:00Z"),ZoneId.of("Asia/Shanghai"));
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(System.getenv("AI_QUOTA_DB_URL"),System.getenv("AI_QUOTA_DB_USER"),System.getenv("AI_QUOTA_DB_PASSWORD"));
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory=new SqlSessionFactoryBean(); factory.setDataSource(source);
            var config=new org.apache.ibatis.session.Configuration(); config.addMapper(AiTrialQuotaMapper.class);
            factory.setConfiguration(config); return factory.getObject();
        }
        @Bean AiTrialQuotaMapper mapper(SqlSessionFactory factory) { return new SqlSessionTemplate(factory).getMapper(AiTrialQuotaMapper.class); }
        @Bean AiTrialProperties properties() { return new AiTrialProperties(); }
        @Bean AiTrialQuotaService quota(AiTrialQuotaMapper mapper,AiTrialProperties properties) { return new AiTrialQuotaService(mapper,properties,CLOCK); }
    }
}
