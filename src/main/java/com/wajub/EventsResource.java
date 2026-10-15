package com.wajub;

import com.wajub.exception.WajubException;

import java.util.Map;

public class EventsResource {

    private final BaseClient base;

    EventsResource(BaseClient base) {
        this.base = base;
    }

    public PagedResult list(Map<String, Object> params) throws WajubException {
        return PagedResult.createPagedList(base, "/events", "events", params);
    }

    public Map<String, Object> retrieve(String id) throws WajubException {
        Map<String, Object> res = base.get("/events/" + id, null);
        return HttpUtils.pickResource(res, "event");
    }

    public Map<String, Object> resend(String id, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.post("/events/" + id + "/resend", null, opts);
        return HttpUtils.pickResource(res, "event");
    }
}
