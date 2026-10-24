package com.itheima.service.feed;

import com.itheima.config.ContentFeedProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Opt-in source policy; allowing the github.com host alone never grants automatic publication. */
@Component
@RequiredArgsConstructor
public class FeedPublishPolicy {
    private final ContentFeedProperties properties;
    public boolean automatic(ContentFeedSource source) {
        return properties.isAutoPublishEnabled() && source.autoPublishEligible()
                && properties.getAutoPublishSources()!=null && properties.getAutoPublishSources().contains(source.key);
    }
}
