package com.itheima.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Admin list projection reuses Article's public metadata fields but omits unset rich-text content
 * and any other unselected properties from JSON. Detail remains the only admin response with content.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminArticleListItem extends Article {
}
