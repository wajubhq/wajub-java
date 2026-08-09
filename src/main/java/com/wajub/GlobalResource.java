package com.wajub;

import com.wajub.exception.WajubException;

import java.util.Map;

public class GlobalResource {

    private final BaseClient base;

    GlobalResource(BaseClient base) {
        this.base = base;
    }

    public Map<String, Object> ping() throws WajubException {
        return base.get("/", null);
    }

    public ListResult channels(Map<String, Object> params) throws WajubException {
        Map<String, Object> res = base.get("/channels", params);
        return HttpUtils.pickList(res, "channels");
    }

    public ListResult countries(Map<String, Object> params) throws WajubException {
        Map<String, Object> res = base.get("/countries", params);
        return HttpUtils.pickList(res, "countries");
    }

    public ListResult currencies(Map<String, Object> params) throws WajubException {
        Map<String, Object> res = base.get("/currencies", params);
        return HttpUtils.pickList(res, "currencies");
    }
}
