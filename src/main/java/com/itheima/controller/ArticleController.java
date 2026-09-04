package com.itheima.controller;

import com.itheima.pojo.Article;
import com.itheima.pojo.ArticleAddDTO;
import com.itheima.pojo.ArticleUpdateDTO;
import com.itheima.pojo.PageBean;
import com.itheima.pojo.Result;
import com.itheima.service.ArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/article")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    /**
     * 新增文章（带参数校验 + 自定义校验）
     */
    @PostMapping("/add")
    public Result<Void> add(@Valid @RequestBody ArticleAddDTO dto) {
        articleService.add(dto);
        return Result.success();
    }

    /**
     * 修改文章（带参数校验）
     * 之前直接收 Article 实体：没有任何校验，空标题能落库，
     * 而且把 userId/viewCount 这些不该由客户端决定的字段也暴露了出去。
     */
    @PutMapping("/update")
    public Result<Void> update(@Valid @RequestBody ArticleUpdateDTO dto) {
        articleService.update(dto);
        return Result.success();
    }

    @GetMapping("/detail/{id}")
    public Result<Article> detail(@PathVariable Long id) {
        return Result.success(articleService.getById(id));
    }

    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.delete(id);
        return Result.success();
    }

    /**
     * 条件分页查询当前用户的文章
     * GET /article/list?pageNum=1&pageSize=5&categoryId=1&state=已发布
     *
     * 注意：这里的 state 收中文「草稿」「已发布」，而 add/update 的 state 是数字 0/1。
     * 给 list 传 0/1 会被 parseState 解析成 null，静默退化成「不筛选」，不会报错。
     */
    @GetMapping("/list")
    public Result<PageBean<Article>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "5") Integer pageSize,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(required = false) String state) {
        return Result.success(articleService.list(pageNum, pageSize, categoryId, state));
    }
}
