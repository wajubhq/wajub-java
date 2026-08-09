package com.wajub;

import com.wajub.exception.WajubException;

import java.util.Map;

public class InvoicesResource extends CrudResource {

    InvoicesResource(BaseClient base) {
        super(base, "invoices", "invoice", "invoices");
    }

    public Map<String, Object> send(String id, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.post("/invoices/" + id + "/send", null, opts);
        return HttpUtils.pickResource(res, "invoice");
    }

    public Map<String, Object> markPaid(String id, Map<String, Object> params, RequestOptions opts)
            throws WajubException {
        Map<String, Object> res = base.post("/invoices/" + id + "/mark-paid", params, opts);
        return HttpUtils.pickResource(res, "invoice");
    }

    public Map<String, Object> cancel(String id, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.post("/invoices/" + id + "/cancel", null, opts);
        return HttpUtils.pickResource(res, "invoice");
    }
}
