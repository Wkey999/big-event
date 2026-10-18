package com.itheima.service.feed;

import com.itheima.mapper.ContentFeedMapper;
import com.itheima.pojo.FeedEntry;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FeedTranslationServiceTest {
    private final ContentFeedMapper mapper=mock(ContentFeedMapper.class);
    private final FeedTitleTranslator translator=mock(FeedTitleTranslator.class);
    private final FeedTranslationService service=new FeedTranslationService(mapper,translator);
    private FeedEntry entry() { var entry=new FeedEntry(); entry.setId(2L); return entry; }
    @Test void savesByIdButNeverPublishes() throws Exception {
        var entries=List.of(entry()); when(mapper.findUntranslated("github",10)).thenReturn(entries);
        when(translator.translate(entries)).thenReturn(Map.of(2L,"构建 Git 基础设施")); when(mapper.saveTranslation(2L,"构建 Git 基础设施")).thenReturn(1);
        assertThat(service.translatePending().translated()).isEqualTo(1);
        verify(mapper,never()).insertLinkArticle(any()); verify(mapper,never()).review(any(),anyInt(),any(),any());
    }
    @Test void alreadyTranslatedBatchDoesNotCallModel() {
        when(mapper.findUntranslated("github",10)).thenReturn(List.of());
        assertThat(service.translatePending().success()).isTrue(); verifyNoInteractions(translator);
    }
    @Test void failureLeavesTitlesPendingAndDoesNotExposeProviderDetails() throws Exception {
        when(mapper.findUntranslated("github",10)).thenReturn(List.of(entry())); when(translator.translate(any())).thenThrow(new IllegalStateException("private provider response"));
        var result=service.translatePending(); assertThat(result.success()).isFalse(); assertThat(result.message()).doesNotContain("private");
        verify(mapper,never()).saveTranslation(any(),any());
    }
    @Test void sportsTranslationIsIsolatedFromGithubAndChineseSourcesSkipModel() throws Exception {
        var entries=List.of(entry()); when(mapper.findUntranslated("bbc_sport",10)).thenReturn(entries);
        when(translator.translate(entries)).thenReturn(Map.of(2L,"决赛战报")); when(mapper.saveTranslation(2L,"决赛战报")).thenReturn(1);
        assertThat(service.translatePending("bbc_sport").translated()).isEqualTo(1);
        verify(mapper,never()).findUntranslated(eq("github"),anyInt()); clearInvocations(mapper,translator);
        assertThat(service.translatePending("ithome").success()).isTrue(); verifyNoInteractions(mapper,translator);
    }
}
