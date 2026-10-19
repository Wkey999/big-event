package com.itheima.service.agent;

import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.CategoryMapper;
import com.itheima.pojo.ArticleSearchRow;
import com.itheima.pojo.UserAiConfig;
import com.itheima.service.AskLogService;
import com.itheima.service.BrowseLogService;
import com.itheima.service.UserProfileService;
import com.itheima.utils.ThreadLocalUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import com.itheima.pojo.AskResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.client.ChatClient;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentAskServiceTest {
    @Mock private AiTrialQuotaService quota;
    @Mock private com.itheima.service.security.ClientAddressResolver clientAddress;
    @Mock
    private AiSearchClientRouter factory;

    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClient.ChatClientRequestSpec prompt;
    @Mock
    private ChatClient.CallResponseSpec response;
    @Mock
    private ArticleMapper articleMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private BrowseLogService browseLogService;
    @Mock
    private UserProfileService userProfileService;
    @Mock
    private AskLogService askLogService;

    @BeforeEach
    void setUserContext() {
        ThreadLocalUtils.setUserId(7L);
    }

    @AfterEach
    void clearUserContext() {
        ThreadLocalUtils.clear();
    }

    @Test
    void responseReferencesComeFromActualToolResultsRatherThanModelText() {
        ArticleTools articleTools = new ArticleTools(articleMapper, categoryMapper, browseLogService);
        ProfileTools profileTools = new ProfileTools(userProfileService);
        AgentAskService service = new AgentAskService(factory, articleTools, profileTools, askLogService,quota,clientAddress);
        UserAiConfig config = new UserAiConfig();
        when(factory.create("custom",config)).thenReturn(chatClient);
        ArticleSearchRow result = new ArticleSearchRow();
        result.setId(41L);
        result.setTitle("Spring engineering");
        result.setSummary("A short summary");
        result.setPublishedAt(LocalDateTime.now());

        when(chatClient.prompt()).thenReturn(prompt);
        when(prompt.system(anyString())).thenReturn(prompt);
        when(prompt.user("find Spring articles")).thenReturn(prompt);
        when(prompt.tools(articleTools, profileTools)).thenAnswer(invocation -> {
            when(articleMapper.searchPublished("Spring", null, null, null, "latest", 0, 6)).thenReturn(List.of(result));
            articleTools.queryArticles("Spring", null, 5, "all", "latest");
            return prompt;
        });
        when(prompt.call()).thenReturn(response);
        when(response.content()).thenReturn("Read the real article 41; never cite made-up article 999.");

        AskResponse askResponse = service.ask("find Spring articles", "custom", config);

        assertThat(askResponse.answer()).contains("999");
        assertThat(askResponse.references()).extracting(reference -> reference.id()).containsExactly(41L);
        org.mockito.Mockito.verifyNoInteractions(quota,clientAddress);
        org.mockito.Mockito.verify(askLogService).recordForModel(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("find Spring articles"), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(true),org.mockito.ArgumentMatchers.isNull());
    }
    @Test void onePlatformSearchReservesOnceBeforeCallingModel() {
        var articles=new ArticleTools(articleMapper,categoryMapper,browseLogService);
        var profiles=new ProfileTools(userProfileService);
        var service=new AgentAskService(factory,articles,profiles,askLogService,quota,clientAddress);
        when(factory.create("platform",null)).thenReturn(chatClient); when(clientAddress.current()).thenReturn("203.0.113.8");
        when(chatClient.prompt()).thenReturn(prompt); when(prompt.system(anyString())).thenReturn(prompt);
        when(prompt.user("hello")).thenReturn(prompt); when(prompt.tools(articles,profiles)).thenReturn(prompt);
        when(prompt.call()).thenReturn(response); when(response.content()).thenReturn("answer");
        service.ask("hello","platform",null);
        var ordered=org.mockito.Mockito.inOrder(quota,chatClient);
        ordered.verify(quota).reserve(7L,"203.0.113.8"); ordered.verify(chatClient).prompt();
        org.mockito.Mockito.verify(quota,org.mockito.Mockito.times(1)).reserve(7L,"203.0.113.8");
    }
    @Test void exhaustedQuotaNeverCallsModel() {
        var service=new AgentAskService(factory,new ArticleTools(articleMapper,categoryMapper,browseLogService),new ProfileTools(userProfileService),askLogService,quota,clientAddress);
        when(factory.create("platform",null)).thenReturn(chatClient); when(clientAddress.current()).thenReturn("203.0.113.8");
        org.mockito.Mockito.doThrow(new com.itheima.exception.BusinessException("次数已用完")).when(quota).reserve(7L,"203.0.113.8");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.ask("hello","platform",null)).hasMessage("次数已用完");
        org.mockito.Mockito.verifyNoInteractions(chatClient);
    }
}
