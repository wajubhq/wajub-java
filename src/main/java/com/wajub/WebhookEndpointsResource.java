package com.wajub;

import com.wajub.exception.WajubException;

import java.util.Map;

public class WebhookEndpointsResource extends CrudResource {

    WebhookEndpointsResource(BaseClient base) {
        super(base, "webhooks", "endpoint", "endpoints");
    }

    public Map<String, Object> rotateSecret(String id, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.post("/webhooks/" + id + "/rotate-secret", null, opts);
        return HttpUtils.pickResource(res, "endpoint");
    }
}
