package com.itheima.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果封装 - 通用分页Bean
 *
 * @param <T> 每页数据的元素类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageBean<T> {
    /** 总记录数 */
    private Long total;
    /** 当前页的数据列表 */
    private List<T> items;
}
