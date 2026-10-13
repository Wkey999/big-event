package com.itheima.controller;

import com.itheima.pojo.ArticleSearchRow;
import com.itheima.pojo.PageBean;
import com.itheima.pojo.Result;
import com.itheima.service.ArticleSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/article")
@RequiredArgsConstructor
@Validated
public class ArticleSearchController {

    private final ArticleSearchService articleSearchService;

    @GetMapping("/search")
    public Result<PageBean<ArticleSearchRow>> search(
            @RequestParam @NotBlank(message = "搜索关键词不能为空") @Size(max = 100, message = "关键词最长100个字符") String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum必须大于0") Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize必须大于0") @Max(value = 100, message = "pageSize不能超过100") Integer pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long authorId,
            @RequestParam(defaultValue = "all") @Pattern(regexp = "all|1d|7d|30d", message = "timeRange仅支持all/1d/7d/30d") String timeRange,
            @RequestParam(defaultValue = "latest") @Pattern(regexp = "latest|popular", message = "orderBy仅支持latest/popular") String orderBy) {
        return Result.success(articleSearchService.search(keyword, pageNum, pageSize,
                categoryId, authorId, timeRange, orderBy));
    }
}
