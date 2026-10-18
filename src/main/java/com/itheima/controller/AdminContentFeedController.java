package com.itheima.controller;
import com.itheima.pojo.*;
import com.itheima.service.feed.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/feeds")
@RequiredArgsConstructor
public class AdminContentFeedController {
    private final ContentFeedService feeds;
    private final FeedPublishingService publishing;
    private final FeedTranslationService translations;
    public record PublishRequest(@NotNull Long categoryId,@Size(max=500) String translatedTitle) {}
    @PostMapping("/{source}/translate")
    public Result<FeedTranslationService.TranslationResult> translate(@PathVariable String source) { return Result.success(translations.translatePending(source)); }
    @GetMapping("/sources")
    public Result<List<Map<String,Object>>> sources() { return Result.success(feeds.sources()); }
    @PostMapping("/{source}/collect")
    public Result<FeedRun> collect(@PathVariable String source) { return Result.success(feeds.collect(source)); }
    @GetMapping("/entries")
    public Result<PageBean<FeedEntry>> entries(@RequestParam(defaultValue="1") Integer pageNum,@RequestParam(required=false) Integer status,@RequestParam(required=false) String source) {
        return Result.success(feeds.entries(pageNum,status,source));
    }
    @PostMapping("/entries/{id}/publish")
    public Result<Long> publish(@PathVariable Long id,@Valid @RequestBody PublishRequest request) { return Result.success(publishing.publish(id,request.categoryId(),request.translatedTitle())); }
    @PostMapping("/entries/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) { publishing.reject(id); return Result.success(); }
}
