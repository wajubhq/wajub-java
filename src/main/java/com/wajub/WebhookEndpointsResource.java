package com.wajub;

import com.wajub.exception.WajubException;

import java.util.Map;

public class WebhookEndpointsResource extends CrudResource {

    WebhookEndpointsResource(BaseClient base) {
        super(base, "webhooks", "endpoint", "endpoints");
    }

    public Map<String, Object> rotateSecret(String id, RequestOptions opts) throws WajubException {
        String path = "/webhooks/" + HttpUtils.pathSegment(id) + "/rotate-secret";
        Map<String, Object> res = base.post(path, null, opts);
        return HttpUtils.pickResource(res, "endpoint");
    }
}
