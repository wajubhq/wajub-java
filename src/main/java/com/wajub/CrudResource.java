package com.wajub;

import com.wajub.exception.WajubException;

import java.util.Map;

public class CrudResource {

    protected final BaseClient base;
    private final String path;
    private final String singular;
    private final String plural;

    CrudResource(BaseClient base, String path, String singular, String plural) {
        this.base = base;
        this.path = path;
        this.singular = singular;
        this.plural = plural;
    }

    public Map<String, Object> create(Map<String, Object> params, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.post("/" + path, params, opts);
        return HttpUtils.pickResource(res, singular);
    }

    public Map<String, Object> retrieve(String id) throws WajubException {
        Map<String, Object> res = base.get("/" + path + "/" + id, null);
        return HttpUtils.pickResource(res, singular);
    }

    public PagedResult list(Map<String, Object> params) throws WajubException {
        return PagedResult.createPagedList(base, "/" + path, plural, params);
    }

    public Map<String, Object> update(String id, Map<String, Object> params, RequestOptions opts)
            throws WajubException {
        Map<String, Object> res = base.put("/" + path + "/" + id, params, opts);
        return HttpUtils.pickResource(res, singular);
    }

    public void delete(String id, RequestOptions opts) throws WajubException {
        base.delete("/" + path + "/" + id, opts);
    }
}
