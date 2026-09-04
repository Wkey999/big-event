package com.itheima.controller;

import com.itheima.pojo.Article;
import com.itheima.pojo.Result;
import com.itheima.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/article")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @PostMapping("/add")
    public Result<Void> add(@RequestBody Article article) {
        articleService.add(article);
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

    @GetMapping("/list")
    public Result<List<Article>> list(@RequestParam(required = false) Long categoryId,
                                      @RequestParam(required = false) String state) {
        return Result.success(articleService.list(categoryId, state));
    }
}
