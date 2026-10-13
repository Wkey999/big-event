package com.itheima.service;

import com.itheima.exception.BusinessException;
import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.CommunityMapper;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.Article;
import com.itheima.pojo.PageBean;
import com.itheima.pojo.PostMedia;
import com.itheima.pojo.PostMediaDTO;
import com.itheima.pojo.TopicSummary;
import com.itheima.pojo.TopicPost;
import com.itheima.pojo.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CommunityMapper communityMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;

    public List<PostMedia> getMedia(Long articleId, Long userId) {
        Article article = communityMapper.findArticle(articleId);
        assertReadable(article, userId);
        return communityMapper.listMedia(articleId);
    }

    @Transactional
    public void replaceMedia(Long articleId, List<PostMediaDTO> media, Long userId) {
        Article article = communityMapper.findArticleForUpdate(articleId);
        assertEditable(article, userId);
        replaceMediaRows(articleId, media);
    }

    @Transactional
    public void applyOptionalFields(Long articleId, List<PostMediaDTO> media, List<String> topicNames,
                                    String existingCoverImg) {
        if (media != null) {
            replaceMediaRows(articleId, media);
        } else {
            syncFirstMediaToCover(articleId, existingCoverImg);
        }
        if (topicNames != null) {
            replaceTopics(articleId, topicNames);
        }
    }

    @Transactional
    public void removeTopicsForDeletedArticle(Long articleId) {
        List<Long> topicIds = communityMapper.listTopicIdsForArticle(articleId);
        if (topicIds.isEmpty()) {
            return;
        }
        communityMapper.deleteAllPostTopics(articleId);
        for (Long topicId : topicIds) {
            if (communityMapper.decrementTopicPostCount(topicId) != 1) {
                throw new IllegalStateException("topic.post_count is inconsistent for topic " + topicId);
            }
        }
    }

    public List<TopicSummary> listTopics(String sort, Integer requestedLimit) {
        String normalizedSort = sort == null || sort.isBlank() ? "hot" : sort.trim().toLowerCase(Locale.ROOT);
        if (!"hot".equals(normalizedSort) && !"new".equals(normalizedSort)) {
            throw new BusinessException("话题排序仅支持 hot 或 new");
        }
        int limit = requestedLimit == null ? 20 : Math.max(1, Math.min(MAX_PAGE_SIZE, requestedLimit));
        return communityMapper.listTopics(normalizedSort, limit);
    }

    public PageBean<TopicPost> topicPosts(Long topicId, Integer requestedPage, Integer requestedSize) {
        if (communityMapper.countVisibleTopic(topicId) == 0) {
            throw new BusinessException("话题不存在");
        }
        int page = requestedPage == null || requestedPage < 1 ? 1 : requestedPage;
        int size = requestedSize == null || requestedSize < 1 ? 10 : Math.min(MAX_PAGE_SIZE, requestedSize);
        long total = communityMapper.countPublishedTopicPosts(topicId);
        int offset = (int) Math.min((long) (page - 1) * size, Integer.MAX_VALUE);
        return new PageBean<>(total, total == 0 ? List.of()
                : communityMapper.listPublishedTopicPosts(topicId, offset, size));
    }

    private void replaceMediaRows(Long articleId, List<PostMediaDTO> media) {
        communityMapper.deleteMedia(articleId);
        for (int i = 0; i < media.size(); i++) {
            PostMediaDTO item = media.get(i);
            if (item.getMediaType() == null) {
                item.setMediaType(1);
            }
            communityMapper.insertMedia(articleId, item, i);
        }
        String cover = media.isEmpty() ? "" : media.get(0).getUrl();
        if (articleMapper.updateCoverImg(articleId, cover) != 1) {
            throw new BusinessException("文章不存在");
        }
    }

    private void syncFirstMediaToCover(Long articleId, String coverImg) {
        String cover = coverImg == null ? "" : coverImg;
        if (cover.isBlank()) {
            communityMapper.deleteMedia(articleId);
            return;
        }
        if (communityMapper.updateFirstMediaUrl(articleId, cover) == 0) {
            PostMediaDTO legacyCover = new PostMediaDTO();
            legacyCover.setUrl(cover);
            legacyCover.setMediaType(1);
            communityMapper.insertMedia(articleId, legacyCover, 0);
        }
    }

    private void replaceTopics(Long articleId, List<String> requestedNames) {
        if (requestedNames.size() > 5) {
            throw new BusinessException("一篇帖子最多添加5个话题");
        }
        Set<String> names = new LinkedHashSet<>();
        for (String requested : requestedNames) {
            String name = requested == null ? "" : requested.trim();
            if (name.isEmpty()) {
                throw new BusinessException("话题名称不能为空");
            }
            if (name.length() > 30) {
                throw new BusinessException("话题名称最长30个字符");
            }
            names.add(name);
        }

        List<Long> currentIds = communityMapper.listTopicIdsForArticle(articleId);
        Set<Long> desiredIds = new LinkedHashSet<>();
        for (String name : names) {
            communityMapper.insertTopicIfAbsent(name);
            Long topicId = communityMapper.findTopicIdByName(name);
            if (topicId == null) {
                throw new IllegalStateException("Topic insert/select did not return an id");
            }
            desiredIds.add(topicId);
        }

        Set<Long> current = new LinkedHashSet<>(currentIds);
        for (Long oldId : current) {
            if (!desiredIds.contains(oldId)) {
                communityMapper.deletePostTopic(articleId, oldId);
                if (communityMapper.decrementTopicPostCount(oldId) != 1) {
                    throw new IllegalStateException("topic.post_count is inconsistent for topic " + oldId);
                }
            }
        }
        for (Long newId : desiredIds) {
            if (!current.contains(newId)) {
                communityMapper.insertPostTopic(articleId, newId);
                communityMapper.incrementTopicPostCount(newId);
            }
        }
    }

    private void assertReadable(Article article, Long userId) {
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        if (Integer.valueOf(1).equals(article.getState()) || isOwnerOrAdmin(article, userId)) {
            return;
        }
        throw new BusinessException("文章不存在");
    }

    private void assertEditable(Article article, Long userId) {
        if (article == null || !isOwnerOrAdmin(article, userId)) {
            throw new BusinessException("文章不存在或无权操作");
        }
    }

    private boolean isOwnerOrAdmin(Article article, Long userId) {
        if (article.getUserId().equals(userId)) {
            return true;
        }
        User user = userId == null ? null : userMapper.findById(userId);
        return user != null && Integer.valueOf(1).equals(user.getRole());
    }
}
