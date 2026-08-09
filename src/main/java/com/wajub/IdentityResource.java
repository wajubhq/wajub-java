package com.wajub;

import com.wajub.exception.WajubException;

import java.util.Map;

public class IdentityResource {

    private final BaseClient base;

    IdentityResource(BaseClient base) {
        this.base = base;
    }

    public Map<String, Object> resolve(Map<String, Object> params, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.post("/identity/resolve", params, opts);
        return HttpUtils.pickResource(res, "identity");
    }

    public Map<String, Object> validate(Map<String, Object> params, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.post("/identity/validate", params, opts);
        return HttpUtils.pickResource(res, "identity");
    }
}
