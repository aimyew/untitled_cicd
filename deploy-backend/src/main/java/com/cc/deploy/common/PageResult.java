package com.cc.deploy.common;

import java.util.List;

/**
 * 分页响应（与 MyBatis-Plus Page 的核心字段兼容）
 */
public class PageResult<T> {
    public long total;
    public long page;
    public long pageSize;
    public List<T> records;

    public static <T> PageResult<T> of(long total, long page, long pageSize, List<T> records) {
        PageResult<T> r = new PageResult<>();
        r.total = total;
        r.page = page;
        r.pageSize = pageSize;
        r.records = records;
        return r;
    }
}
