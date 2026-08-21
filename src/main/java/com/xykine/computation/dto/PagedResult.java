package com.xykine.computation.dto;

import lombok.Data;
import java.util.List;

@Data
public class PagedResult<T> {
    private List<T> data;
    private int page;
    private int size;
    private long total;
    private long totalPages;

    public static <T> PagedResult<T> of(List<T> data, int page, int size, long total) {
        PagedResult<T> r = new PagedResult<>();
        r.data = data;
        r.page = page;
        r.size = size;
        r.total = total;
        r.totalPages = (long) Math.ceil(total / (double) size);
        return r;
    }
}
