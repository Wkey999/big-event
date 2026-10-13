package com.itheima.controller;

import com.itheima.pojo.CommentDTO;
import com.itheima.pojo.CommentReplyView;
import com.itheima.pojo.CommentView;
import com.itheima.pojo.PageBean;
import com.itheima.pojo.Result;
import com.itheima.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/article/{id}/comments")
    public Result<PageBean<CommentView>> list(@PathVariable Long id,
                                              @RequestParam(required = false) Integer pageNum,
                                              @RequestParam(required = false) Integer pageSize) {
        return Result.success(commentService.listRoots(id, pageNum, pageSize));
    }

    @GetMapping("/comment/{rootId}/replies")
    public Result<PageBean<CommentReplyView>> replies(@PathVariable Long rootId,
                                                       @RequestParam(required = false) Integer pageNum,
                                                       @RequestParam(required = false) Integer pageSize) {
        return Result.success(commentService.listReplies(rootId, pageNum, pageSize));
    }

    @PostMapping("/article/{id}/comment")
    public Result<CommentView> add(@PathVariable Long id, @Valid @RequestBody CommentDTO dto) {
        return Result.success(commentService.add(id, dto));
    }

    @DeleteMapping("/comment/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return Result.success();
    }

    @PostMapping("/comment/{id}/like")
    public Result<Void> like(@PathVariable Long id) {
        commentService.like(id, true);
        return Result.success();
    }

    @DeleteMapping("/comment/{id}/like")
    public Result<Void> unlike(@PathVariable Long id) {
        commentService.like(id, false);
        return Result.success();
    }
}
