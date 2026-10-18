package com.itheima.service.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.pojo.FeedEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeedTitleTranslatorTest {
    private final FeedTitleTranslator translator=new FeedTitleTranslator(mock(ObjectProvider.class),new ObjectMapper());
    private FeedEntry entry(long id) { var entry=new FeedEntry(); entry.setId(id); entry.setTitle("Building Git infrastructure"); return entry; }
    @Test void matchesReorderedChineseResultsByIdAndRetainsOriginals() throws Exception {
        var first=entry(1); var second=entry(2);
        var result=translator.parse("{\"titles\":[{\"id\":2,\"title\":\"构建 Git 基础设施\"},{\"id\":1,\"title\":\"智能体开发实践\"}]}",List.of(first,second));
        assertThat(result.get(1L)).isEqualTo("智能体开发实践");
        assertThat(first.getTitle()).isEqualTo("Building Git infrastructure");
    }
    @Test void rejectsMissingInventedOrDuplicateIds() {
        for(String response:List.of("{\"titles\":[]}","{\"titles\":[{\"id\":3,\"title\":\"中文\"}]}","{\"titles\":[{\"id\":1,\"title\":\"中文\"},{\"id\":1,\"title\":\"中文\"}]}"))
            assertThatThrownBy(() -> translator.parse(response,List.of(entry(1)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsEnglishOnlyMarkupMultilineAndExcessivelyLongOutput() {
        for(String title:List.of("Still English","<b>中文</b>","中文\n解释","中".repeat(501)))
            assertThat(FeedTitleTranslator.validTitle(title)).isFalse();
        assertThat(FeedTitleTranslator.validTitle("GitHub Copilot 的新功能")).isTrue();
    }
    @Test void missingProviderDoesNotInventTranslation() {
        assertThatThrownBy(() -> translator.translate(List.of(entry(1)))).hasMessageContaining("大模型配置");
    }
}
