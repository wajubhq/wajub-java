package com.wajub;

import com.wajub.exception.WajubException;

import java.util.Map;

public class ListenResource {

    private final BaseClient base;

    ListenResource(BaseClient base) {
        this.base = base;
    }

    public Map<String, Object> config() throws WajubException {
        Map<String, Object> res = base.get("/listen/config", null);
        return HttpUtils.pickResource(res, "config", "listen");
    }

    public Map<String, Object> auth(Map<String, Object> params, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.post("/listen/auth", params, opts);
        return HttpUtils.pickResource(res, "auth", "listen");
    }
}
