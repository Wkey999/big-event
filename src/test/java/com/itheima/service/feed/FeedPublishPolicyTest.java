package com.itheima.service.feed;

import com.itheima.config.ContentFeedProperties;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;

class FeedPublishPolicyTest {
    @Test void onlyExplicitOfficialSourcesCanBeEnabledAndMasterSwitchStopsAll() {
        var properties=new ContentFeedProperties();
        properties.setAutoPublishEnabled(true);
        properties.setAutoPublishSources(Arrays.stream(ContentFeedSource.values()).map(s -> s.key).collect(Collectors.toSet()));
        var policy=new FeedPublishPolicy(properties);
        for(var source:ContentFeedSource.values())
            assertThat(policy.automatic(source)).isEqualTo(source==ContentFeedSource.GITHUB || source==ContentFeedSource.DEEPSEEK_GITHUB);
        properties.setAutoPublishSources(java.util.Set.of("qwen_github"));
        assertThat(policy.automatic(ContentFeedSource.GITHUB)).isFalse();
        assertThat(policy.automatic(ContentFeedSource.QWEN_GITHUB)).isFalse();
        properties.setAutoPublishEnabled(false);
        assertThat(policy.automatic(ContentFeedSource.QWEN_GITHUB)).isFalse();
    }
}
