package com.itheima.service.feed;
import com.itheima.exception.BusinessException;
import com.itheima.mapper.*;
import com.itheima.pojo.*;
import com.itheima.utils.ThreadLocalUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedPublishingService {
    private final ContentFeedMapper mapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final FeedPublishPolicy policy;
    @Transactional
    public Long publish(Long id,Long categoryId) {
        return publish(id,categoryId,null);
    }
    @Transactional
    public Long publish(Long id,Long categoryId,String translatedTitle) {
        Long reviewer=requireAdmin(); FeedEntry entry=mapper.lockEntry(id);
        if(entry==null) throw new BusinessException("采集内容不存在");
        if(entry.getStatus()==1) return entry.getArticleId();
        if(entry.getStatus()!=0) throw new BusinessException("已忽略的内容不能直接发布");
        ContentFeedSource source=ContentFeedSource.from(entry.getSourceKey());
        if(source.requiresTranslation) {
            String title=translatedTitle==null?entry.getTranslatedTitle():translatedTitle.strip();
            if(!FeedTitleTranslator.validTitle(title)) throw new BusinessException("请先补译或填写有效的中文标题，再审核发布");
            entry.setTranslatedTitle(title); mapper.updateReviewedTitle(entry);
        }
        return publishLocked(id,entry,source,categoryId,reviewer,false);
    }
    /** Internal scheduler path. No HTTP endpoint accepts an automatic-publish bypass flag. */
    @Transactional
    public boolean publishAutomatically(Long id) {
        FeedEntry entry=mapper.lockEntry(id);
        if(entry==null) throw new BusinessException("采集内容不存在");
        ContentFeedSource source=ContentFeedSource.from(entry.getSourceKey());
        if(!policy.automatic(source)) throw new BusinessException("此来源不在自动发布白名单中");
        if(!Integer.valueOf(0).equals(entry.getStatus()) || !Integer.valueOf(1).equals(entry.getAutoPublishRequested())) return false;
        var raw=new com.rometools.rome.feed.synd.SyndEntryImpl(); raw.setTitle(entry.getTitle()); raw.setLink(entry.getSourceUrl());
        if(ContentFeedService.toEntry(source,raw)==null) throw new BusinessException("来源链接不符合白名单要求");
        if(source.requiresTranslation && !FeedTitleTranslator.validTitle(entry.getTranslatedTitle()))
            throw new BusinessException("中文标题尚未就绪，保留待处理");
        Category category=categoryMapper.findByName(source.categoryName);
        if(category==null) throw new BusinessException("默认频道不存在，保留待处理");
        publishLocked(id,entry,source,category.getId(),null,true);
        return true;
    }
    private Long publishLocked(Long id,FeedEntry entry,ContentFeedSource source,Long categoryId,Long reviewer,boolean automatic) {
        Category category=categoryMapper.findByIdForUpdate(categoryId);
        if(category==null || !Integer.valueOf(1).equals(category.getStatus())) throw new BusinessException("请选择可用的频道");
        if(automatic && !source.categoryName.equals(category.getCategoryName())) throw new BusinessException("默认频道已变更，保留待处理");
        // A stable row serializes lazy creation across entries and reviewers.
        var admins=userMapper.lockActiveAdminIds();
        if(!automatic && !admins.contains(reviewer)) throw new BusinessException("需要管理员权限");
        String username="rss_"+source.key; User bot=userMapper.findByUsername(username);
        if(bot==null) {
            bot=new User(); bot.setUsername(username); bot.setNickname(source.botNickname);
            bot.setPassword(new BCryptPasswordEncoder().encode(UUID.randomUUID().toString())); mapper.insertBot(bot);
        } else if(!Integer.valueOf(1).equals(bot.getUserType()) || !Integer.valueOf(0).equals(bot.getRole())
                || !Integer.valueOf(0).equals(bot.getLoginEnabled()) || !Integer.valueOf(1).equals(bot.getStatus())) {
            throw new BusinessException("机器作者账号状态不符合要求，请检查账号设置");
        }
        String displayTitle=entry.getTranslatedTitle()==null?entry.getTitle():entry.getTranslatedTitle();
        Article article=new Article(); article.setTitle(displayTitle.substring(0,Math.min(50,displayTitle.length())));
        article.setUserId(bot.getId()); article.setCategoryId(categoryId); mapper.insertLinkArticle(article);
        entry.setArticleId(article.getId()); mapper.insertArticleSource(entry);
        if(automatic) {
            if(mapper.publishAutomatically(id,article.getId())!=1) throw new BusinessException("内容状态已改变，请刷新后重试");
        } else if(mapper.review(id,1,article.getId(),reviewer)!=1) throw new BusinessException("内容状态已改变，请刷新后重试");
        return article.getId();
    }
    @Transactional
    public void reject(Long id) {
        Long reviewer=requireAdmin(); FeedEntry entry=mapper.lockEntry(id);
        if(entry==null) throw new BusinessException("采集内容不存在");
        if(entry.getStatus()==2) return;
        if(entry.getStatus()!=0) throw new BusinessException("已发布内容请在内容管理中下架");
        mapper.review(id,2,null,reviewer);
    }
    private Long requireAdmin() {
        Long id=ThreadLocalUtils.getUserId(); User user=id==null?null:userMapper.findById(id);
        if(user==null || !Integer.valueOf(1).equals(user.getRole()) || !Integer.valueOf(1).equals(user.getStatus())
                || !Integer.valueOf(1).equals(user.getLoginEnabled())) throw new BusinessException("需要管理员权限");
        return id;
    }
}
