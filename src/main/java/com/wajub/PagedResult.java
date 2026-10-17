package com.wajub;

import com.wajub.exception.WajubException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Holds a page of list results. */
public class PagedResult {

    private final List<Map<String, Object>> data;
    private final Map<String, Object> meta;
    private final boolean hasMore;
    private final Function<Integer, PagedResult> fetchPage;

    PagedResult(List<Map<String, Object>> data, Map<String, Object> meta,
                boolean hasMore, Function<Integer, PagedResult> fetchPage) {
        this.data = data != null ? List.copyOf(data) : List.of();
        this.meta = meta;
        this.hasMore = hasMore;
        this.fetchPage = fetchPage;
    }

    public List<Map<String, Object>> getData() {
        return data;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public boolean hasMore() {
        return hasMore;
    }

    /** Returns the next page of results. */
    public PagedResult getNextPage() throws WajubException {
        if (!hasMore) {
            throw new IllegalStateException("No more pages available");
        }
        int current = 1;
        if (meta != null && meta.get("current_page") instanceof Number n) {
            current = n.intValue();
        }
        return fetchPage.apply(current + 1);
    }

    /** Yields all items across pages. */
    public List<Map<String, Object>> autoPaging() throws WajubException {
        List<Map<String, Object>> all = new ArrayList<>();
        PagedResult page = this;
        while (true) {
            all.addAll(page.getData());
            if (!page.hasMore()) {
                break;
            }
            page = page.getNextPage();
        }
        return all;
    }

    static PagedResult createPagedList(
            BaseClient client,
            String path,
            String pluralKey,
            Map<String, Object> params) throws WajubException {

        int startPage = 1;
        if (params != null && params.get("page") instanceof Number n) {
            startPage = n.intValue();
        }

        return fetchPage(client, path, pluralKey, params, startPage);
    }

    private static PagedResult fetchPage(
            BaseClient client,
            String path,
            String pluralKey,
            Map<String, Object> params,
            int pageNum) throws WajubException {

        Map<String, Object> query = params != null ? new HashMap<>(params) : new HashMap<>();
        query.put("page", pageNum);

        Map<String, Object> res = client.get(path, query);
        ListResult list = HttpUtils.pickList(res, pluralKey);

        boolean hasMore = false;
        if (list.meta() != null) {
            Object currentObj = list.meta().get("current_page");
            Object lastObj = list.meta().get("last_page");
            if (currentObj instanceof Number current && lastObj instanceof Number last) {
                hasMore = current.intValue() < last.intValue();
            }
        }

        return new PagedResult(
                list.data(),
                list.meta(),
                hasMore,
                nextPage -> fetchPage(client, path, pluralKey, params, nextPage));
    }
}
