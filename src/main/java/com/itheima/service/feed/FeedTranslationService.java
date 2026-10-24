package com.itheima.service.feed;

import com.itheima.mapper.ContentFeedMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedTranslationService {
    private final ContentFeedMapper mapper;
    private final FeedTitleTranslator translator;
    private final ReentrantLock translating=new ReentrantLock();
    public record TranslationResult(int translated,int pending,boolean success,String message) {}

    public TranslationResult translatePending() {
        return translatePending("github");
    }
    public TranslationResult translatePending(String sourceKey) {
        if(!ContentFeedSource.from(sourceKey).requiresTranslation)
            return new TranslationResult(0,0,true,"此来源已提供中文标题，无需补译");
        if(!translating.tryLock()) return new TranslationResult(0,0,false,"标题正在翻译，请稍后刷新");
        int saved=0;
        try {
            var entries=mapper.findUntranslated(sourceKey,10);
            if(entries.isEmpty()) return new TranslationResult(0,0,true,"没有需要补译的待处理标题");
            var titles=translator.translate(entries);
            // Network work stays outside transactions; updates never overwrite a reviewed/published row.
            for(var entry:entries) saved+=mapper.saveTranslation(entry.getId(),titles.get(entry.getId()));
            return new TranslationResult(saved,mapper.countUntranslated(sourceKey),true,"中文标题已生成");
        } catch(Exception e) {
            log.warn("订阅标题翻译未完成 type={}",e.getClass().getSimpleName());
            return new TranslationResult(saved,mapper.countUntranslated(sourceKey),false,"标题翻译暂未完成，原题已保留；可稍后补译或在审核时填写中文标题");
        } finally { translating.unlock(); }
    }
}
