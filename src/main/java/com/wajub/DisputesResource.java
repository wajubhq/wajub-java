package com.wajub;

import com.wajub.exception.WajubException;

import java.util.Map;

public class DisputesResource {

    private final BaseClient base;

    DisputesResource(BaseClient base) {
        this.base = base;
    }

    public PagedResult list(Map<String, Object> params) throws WajubException {
        return PagedResult.createPagedList(base, "/disputes", "disputes", params);
    }

    public Map<String, Object> retrieve(String id) throws WajubException {
        Map<String, Object> res = base.get("/disputes/" + id, null);
        return HttpUtils.pickResource(res, "dispute");
    }

    public Map<String, Object> submitEvidence(String id, Map<String, Object> params, RequestOptions opts)
            throws WajubException {
        Map<String, Object> res = base.post("/disputes/" + id + "/submit-evidence", params, opts);
        return HttpUtils.pickResource(res, "dispute");
    }

    public Map<String, Object> accept(String id, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.post("/disputes/" + id + "/accept", null, opts);
        return HttpUtils.pickResource(res, "dispute");
    }

    public Map<String, Object> close(String id, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.post("/disputes/" + id + "/close", null, opts);
        return HttpUtils.pickResource(res, "dispute");
    }

    public Map<String, Object> sendMessage(String id, Map<String, Object> params, RequestOptions opts)
            throws WajubException {
        Map<String, Object> res = base.post("/disputes/" + id + "/messages", params, opts);
        return HttpUtils.pickResource(res, "dispute");
    }
}
