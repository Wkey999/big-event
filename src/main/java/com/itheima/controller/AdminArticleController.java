package com.itheima.controller;

import com.itheima.pojo.AdminArticleUpdateDTO;
import com.itheima.pojo.Article;
import com.itheima.pojo.ArticleStateDTO;
import com.itheima.pojo.PageBean;
import com.itheima.pojo.Result;
import com.itheima.service.AdminArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/article")
@RequiredArgsConstructor
public class AdminArticleController {

    private final AdminArticleService adminArticleService;

    @GetMapping("/list")
    public Result<PageBean<Article>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "10") Integer pageSize,
                                          @RequestParam(required = false) Integer state,
                                          @RequestParam(required = false) Long authorId,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(required = false) String keyword) {
        return Result.success(adminArticleService.list(pageNum, pageSize, state, authorId, categoryId, keyword));
    }

    @GetMapping("/{id}")
    public Result<Article> detail(@PathVariable Long id) {
        return Result.success(adminArticleService.detail(id));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AdminArticleUpdateDTO dto) {
        adminArticleService.update(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminArticleService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/state")
    public Result<Void> updateState(@PathVariable Long id, @Valid @RequestBody ArticleStateDTO dto) {
        adminArticleService.updateState(id, dto.getState());
        return Result.success();
    }
}
