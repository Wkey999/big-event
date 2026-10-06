package com.itheima.service;

import com.itheima.pojo.BrowseLogDTO;

public interface BrowseLogService {

    /**
     * 上报一次浏览/曝光。同一用户对同一文章 60 秒内的多次上报合并为一行，
     * 停留时长取最大值（曝光 0 → 关闭时真实时长）。
     */
    void report(BrowseLogDTO dto);
}
