package com.itheima.controller;

import com.itheima.pojo.ArticleActionDTO;
import com.itheima.pojo.Result;
import com.itheima.service.ArticleActionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 文章互动（阶段 A 数据闭环的第二个信号源：点赞/收藏比曝光更"重"，是画像里的高权重信号）
 */
@RestController
@RequestMapping("/article")
@RequiredArgsConstructor
public class ArticleActionController {

    private final ArticleActionService articleActionService;

    /**
     * POST /article/action
     * body: {"articleId":1,"actionType":1,"op":1}   actionType 1-点赞 2-收藏；op 1-执行 0-取消
     * 幂等：重复点赞不会重复计数，取消未点赞的也不报错
     */
    @PostMapping("/action")
    public Result<Void> action(@Valid @RequestBody ArticleActionDTO dto) {
        articleActionService.act(dto);
        return Result.success();
    }
}
