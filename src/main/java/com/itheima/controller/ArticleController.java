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

    /**
     * 编辑预填：返回完整正文（含 content），且不增加浏览量。
     * 列表接口已收窄列不再返回 content，编辑弹窗靠这个接口回填——
     * 不能复用 /detail：那个接口每调一次浏览量 +1，作者编辑自己的文章会把自己的浏览量刷高。
     */
    @GetMapping("/edit/{id}")
    public Result<Article> editDetail(@PathVariable Long id) {
        return Result.success(articleService.getForEdit(id));
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
     * state 为可选筛选条件，支持与新增/修改一致的数字 0/1，也兼容旧前端中文「草稿」「已发布」。
     * 其他非空值会明确报错，不再静默退化成「不筛选」。
     * mode 可选 latest/recommend；缺省为 latest。
     */
    @GetMapping("/list")
    public Result<PageBean<Article>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "5") Integer pageSize,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(required = false) String state,
                                          @RequestParam(defaultValue = "latest") String mode) {
        return Result.success(articleService.list(pageNum, pageSize, categoryId, state, mode));
    }
}
