package com.itheima.controller;

import com.itheima.pojo.MediaReplaceDTO;
import com.itheima.pojo.PageBean;
import com.itheima.pojo.PostMedia;
import com.itheima.pojo.Result;
import com.itheima.pojo.TopicSummary;
import com.itheima.pojo.TopicPost;
import com.itheima.service.CommunityService;
import com.itheima.utils.ThreadLocalUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @GetMapping("/article/{id}/media")
    public Result<List<PostMedia>> media(@PathVariable Long id) {
        return Result.success(communityService.getMedia(id, ThreadLocalUtils.getUserId()));
    }

    @PutMapping("/article/{id}/media")
    public Result<Void> replaceMedia(@PathVariable Long id, @Valid @RequestBody MediaReplaceDTO dto) {
        communityService.replaceMedia(id, dto.getMedia() == null ? List.of() : dto.getMedia(),
                ThreadLocalUtils.getUserId());
        return Result.success();
    }

    @GetMapping("/topic/list")
    public Result<List<TopicSummary>> topics(@RequestParam(required = false) String sort,
                                             @RequestParam(required = false) Integer limit) {
        return Result.success(communityService.listTopics(sort, limit));
    }

    @GetMapping("/topic/{id}/posts")
    public Result<PageBean<TopicPost>> topicPosts(@PathVariable Long id,
                                                 @RequestParam(required = false) Integer pageNum,
                                                 @RequestParam(required = false) Integer pageSize) {
        return Result.success(communityService.topicPosts(id, pageNum, pageSize));
    }
}
