package com.itheima.service.agent;

import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.CategoryMapper;
import com.itheima.pojo.ArticleSearchRow;
import com.itheima.service.UserProfileService;
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
    private UserProfileService userProfileService;

    @Test
    void responseReferencesComeFromActualToolResultsRatherThanModelText() {
        ArticleTools articleTools = new ArticleTools(articleMapper, categoryMapper);
        ProfileTools profileTools = new ProfileTools(userProfileService);
        AgentAskService service = new AgentAskService(chatClient, articleTools, profileTools);
        ArticleSearchRow result = new ArticleSearchRow();
        result.setId(41L);
        result.setTitle("Spring engineering");
        result.setSummary("A short summary");
        result.setPublishedAt(LocalDateTime.now());

        when(chatClient.prompt()).thenReturn(prompt);
        when(prompt.system(anyString())).thenReturn(prompt);
        when(prompt.user("find Spring articles")).thenReturn(prompt);
        when(prompt.tools(articleTools, profileTools)).thenAnswer(invocation -> {
            when(articleMapper.searchPublished("Spring", null, 6)).thenReturn(List.of(result));
            articleTools.queryArticles("Spring", null, 5);
            return prompt;
        });
        when(prompt.call()).thenReturn(response);
        when(response.content()).thenReturn("Read the real article 41; never cite made-up article 999.");

        AskResponse askResponse = service.ask("find Spring articles");

        assertThat(askResponse.answer()).contains("999");
        assertThat(askResponse.references()).extracting(reference -> reference.id()).containsExactly(41L);
    }
}
