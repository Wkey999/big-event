package com.itheima.pojo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminArticleListItemTest {

    @Test
    void adminArticleListJsonOmitsRichTextContentWhenMapperDoesNotSelectIt() throws Exception {
        AdminArticleListItem item = new AdminArticleListItem();
        item.setId(8L);
        item.setTitle("metadata only");
        item.setContent(null);

        String json = new ObjectMapper().writeValueAsString(item);

        assertThat(json).contains("metadata only");
        assertThat(json).doesNotContain("content");
    }
}
