package com.itheima.service;

import com.itheima.pojo.ArticleActionDTO;

public interface ArticleActionService {

    /**
     * 点赞 / 收藏（op=1 执行，op=0 取消）。
     * 幂等：重复点赞不重复计数，取消未点赞的也不报错。
     */
    void act(ArticleActionDTO dto);
}
