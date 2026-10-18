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
        Category category=categoryMapper.findByIdForUpdate(categoryId);
        if(category==null || !Integer.valueOf(1).equals(category.getStatus())) throw new BusinessException("请选择可用的频道");
        // A stable row serializes lazy creation across entries and reviewers.
        if(!userMapper.lockActiveAdminIds().contains(reviewer)) throw new BusinessException("需要管理员权限");
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
        entry.setArticleId(article.getId()); mapper.insertArticleSource(entry); mapper.review(id,1,article.getId(),reviewer);
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
