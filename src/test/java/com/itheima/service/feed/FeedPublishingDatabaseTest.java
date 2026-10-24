package com.itheima.service.feed;

import com.itheima.config.ContentFeedProperties;
import com.itheima.mapper.*;
import com.itheima.pojo.FeedEntry;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Opt-in MySQL test. Creates only its own UUID-tagged entry and removes its articles afterwards. */
@EnabledIfEnvironmentVariable(named="AI_QUOTA_DB_TEST",matches="true")
class FeedPublishingDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    ContentFeedMapper mapper;
    FeedPublishingService service;
    FeedEntry entry;
    String title;
    @BeforeEach void setup() {
        context=new AnnotationConfigApplicationContext(Config.class);
        jdbc=new JdbcTemplate(context.getBean(DataSource.class));
        mapper=context.getBean(ContentFeedMapper.class); service=context.getBean(FeedPublishingService.class);
        String unique=UUID.randomUUID().toString().replace("-","");
        title="自动发布验收"+unique;
        entry=new FeedEntry(); entry.setSourceKey("github"); entry.setSourceName("GitHub 官方博客");
        entry.setSourceUrl("https://github.blog/engineering/big-event-test-"+unique+"/"); entry.setUrlHash(unique.repeat(2));
        entry.setTitle("AI engineering test "+unique); entry.setSourceAuthor("GitHub");
        entry.setSourcePublishedAt(LocalDateTime.now()); entry.setFetchedAt(LocalDateTime.now()); entry.setAutoPublishRequested(1);
        mapper.insertEntry(entry); mapper.saveTranslation(entry.getId(),title);
    }
    @AfterEach void cleanup() {
        if(jdbc!=null && entry!=null && entry.getId()!=null) {
            var ids=jdbc.queryForList("SELECT id FROM article WHERE title=?",Long.class,title);
            for(Long id:ids) { jdbc.update("DELETE FROM article_source WHERE article_id=?",id); jdbc.update("DELETE FROM article WHERE id=?",id); }
            jdbc.update("DELETE FROM feed_entry WHERE id=? AND source_url=?",entry.getId(),entry.getSourceUrl());
        }
        if(context!=null) context.close();
    }
    @Test void concurrentAutomaticPublicationCreatesExactlyOneArticleAndAnAuditableSystemRecord() throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            var go=new CountDownLatch(1);
            Callable<Boolean> publish=() -> { go.await(); return service.publishAutomatically(entry.getId()); };
            var one=executor.submit(publish); var two=executor.submit(publish); go.countDown();
            assertThat(java.util.List.of(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM article WHERE title=?",Integer.class,title)).isEqualTo(1);
        var saved=jdbc.queryForMap("SELECT status,publish_mode,reviewed_by,article_id FROM feed_entry WHERE id=?",entry.getId());
        assertThat(((Number)saved.get("status")).intValue()).isEqualTo(1);
        assertThat(((Number)saved.get("publish_mode")).intValue()).isEqualTo(1);
        assertThat(saved.get("reviewed_by")).isNull(); assertThat(saved.get("article_id")).isNotNull();
    }
    @Test void failedFinalTransitionRollsBackArticleAndSourceSnapshot() {
        doReturn(0).when(mapper).publishAutomatically(eq(entry.getId()),anyLong());
        assertThatThrownBy(() -> service.publishAutomatically(entry.getId())).hasMessageContaining("状态已改变");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM article WHERE title=?",Integer.class,title)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM article_source WHERE source_url=?",Integer.class,entry.getSourceUrl())).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM feed_entry WHERE id=?",Integer.class,entry.getId())).isZero();
    }
    @Configuration
    @EnableTransactionManagement(proxyTargetClass=true)
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(System.getenv("AI_QUOTA_DB_URL"),System.getenv("AI_QUOTA_DB_USER"),System.getenv("AI_QUOTA_DB_PASSWORD")); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean SqlSessionFactory factory(DataSource source) throws Exception {
            var factory=new SqlSessionFactoryBean(); factory.setDataSource(source);
            var configuration=new org.apache.ibatis.session.Configuration(); configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(ContentFeedMapper.class); configuration.addMapper(UserMapper.class); configuration.addMapper(CategoryMapper.class);
            factory.setConfiguration(configuration); return factory.getObject();
        }
        @Bean ContentFeedMapper feeds(SqlSessionFactory factory) { return mock(ContentFeedMapper.class,org.mockito.AdditionalAnswers.delegatesTo(new SqlSessionTemplate(factory).getMapper(ContentFeedMapper.class))); }
        @Bean UserMapper users(SqlSessionFactory factory) { return new SqlSessionTemplate(factory).getMapper(UserMapper.class); }
        @Bean CategoryMapper categories(SqlSessionFactory factory) { return new SqlSessionTemplate(factory).getMapper(CategoryMapper.class); }
        @Bean FeedPublishPolicy policy() { var properties=new ContentFeedProperties(); properties.setAutoPublishEnabled(true); return new FeedPublishPolicy(properties); }
        @Bean FeedPublishingService publishing(ContentFeedMapper feeds,UserMapper users,CategoryMapper categories,FeedPublishPolicy policy) { return new FeedPublishingService(feeds,users,categories,policy); }
    }
}
