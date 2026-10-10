package com.itheima.service.impl;

import com.itheima.exception.BusinessException;
import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.BrowseLogMapper;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.Article;
import com.itheima.pojo.BrowseLogDTO;
import com.itheima.pojo.User;
import com.itheima.service.BrowseLogService;
import com.itheima.utils.ThreadLocalUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BrowseLogServiceImpl implements BrowseLogService {

    /** 同一用户对同一文章在这个窗口内的多次上报合并成一行 */
    private static final int DEDUP_SECONDS = 60;

    private final BrowseLogMapper browseLogMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;

    @Override
    public void report(BrowseLogDTO dto) {
        Long userId = ThreadLocalUtils.getUserId();
        Article article = articleMapper.findById(dto.getArticleId());

        // 权限与阅读规则保持一致：只能对「自己看得到的文章」埋点。
        // 否则这个接口会变成探测他人草稿是否存在、甚至给任意 id 刷行为数据的口子。
        // 无权限时与「不存在」回同一条模糊错误（沿用 ArticleService 的既有做法）。
        if (article == null || !visibleTo(article, userId)) {
            throw new BusinessException("文章不存在");
        }

        int dwellMs = dto.getDwellMs() == null ? 0 : dto.getDwellMs();
        String mode = dto.getMode() == null ? "latest" : dto.getMode();
        LocalDateTime since = LocalDateTime.now().minusSeconds(DEDUP_SECONDS);

        Long recentId = browseLogMapper.findRecentId(userId, article.getId(), mode, since);
        if (recentId != null) {
            browseLogMapper.updateDwell(recentId, dwellMs);
            return;
        }
        browseLogMapper.insert(userId, article.getId(), article.getCategoryId(), dwellMs, mode);
    }

    /** 已发布任何人都能看；草稿只有作者本人和管理员能看 */
    private boolean visibleTo(Article article, Long userId) {
        if (Integer.valueOf(1).equals(article.getState())) {
            return true;
        }
        if (article.getUserId().equals(userId)) {
            return true;
        }
        User me = userMapper.findById(userId);
        return me != null && Integer.valueOf(1).equals(me.getRole());
    }
}
