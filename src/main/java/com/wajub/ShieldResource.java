package com.wajub;

import com.wajub.exception.WajubException;

import java.util.Map;

public class ShieldResource {

    private final BaseClient base;

    ShieldResource(BaseClient base) {
        this.base = base;
    }

    public Map<String, Object> getSettings() throws WajubException {
        Map<String, Object> res = base.get("/shield/settings", null);
        return HttpUtils.pickResource(res, "shield", "settings");
    }

    public Map<String, Object> updateSettings(Map<String, Object> params, RequestOptions opts)
            throws WajubException {
        Map<String, Object> res = base.put("/shield/settings", params, opts);
        return HttpUtils.pickResource(res, "shield", "settings");
    }

    public Map<String, Object> stats(Map<String, Object> params) throws WajubException {
        Map<String, Object> res = base.get("/shield/stats", params);
        return HttpUtils.pickResource(res, "shield", "stats");
    }

    public ListResult listBlocklist(Map<String, Object> params) throws WajubException {
        Map<String, Object> res = base.get("/shield/blocklist", params);
        return HttpUtils.pickList(res, "blocklist", "entries");
    }

    public Map<String, Object> addToBlocklist(Map<String, Object> params, RequestOptions opts)
            throws WajubException {
        Map<String, Object> res = base.post("/shield/blocklist", params, opts);
        return HttpUtils.pickResource(res, "blocklist", "entry");
    }

    public void removeFromBlocklist(String id, RequestOptions opts) throws WajubException {
        base.delete("/shield/blocklist/" + id, opts);
    }
}
