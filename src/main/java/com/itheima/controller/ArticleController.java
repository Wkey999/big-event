package com.itheima.controller;

import com.itheima.pojo.Article;
import com.itheima.pojo.ArticleAddDTO;
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

    @PutMapping("/update")
    public Result<Void> update(@RequestBody Article article) {
        articleService.update(article);
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
     */
    @GetMapping("/list")
    public Result<PageBean<Article>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "5") Integer pageSize,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(required = false) String state) {
        return Result.success(articleService.list(pageNum, pageSize, categoryId, state));
    }
}
