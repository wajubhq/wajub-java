package com.wajub;

import com.wajub.exception.WajubException;

import java.util.Map;

public class PaymentsResource {

    private final BaseClient base;

    PaymentsResource(BaseClient base) {
        this.base = base;
    }

    public Payment create(Map<String, Object> params, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.post("/payments", params, opts);
        return Payment.parsePayment(res);
    }

    public Payment initialize(Map<String, Object> params, RequestOptions opts) throws WajubException {
        return create(params, opts);
    }

    public Payment retrieve(String id) throws WajubException {
        Map<String, Object> res = base.get("/payments/" + id, null);
        return Payment.parsePayment(Map.of("transaction", HttpUtils.pickResource(res, "transaction")));
    }

    public PagedResult list(Map<String, Object> params) throws WajubException {
        return PagedResult.createPagedList(base, "/payments", "transactions", params);
    }

    public Payment cancel(String id, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.delete("/payments/" + id, opts);
        return Payment.parsePayment(Map.of("transaction", HttpUtils.pickResource(res, "transaction")));
    }

    public Payment process(String id, Map<String, Object> params, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.post("/payments/" + id, params, opts);
        return Payment.parsePayment(Map.of("transaction", HttpUtils.pickResource(res, "transaction")));
    }

    public Payment processSplit(String id, Map<String, Object> params, RequestOptions opts)
            throws WajubException {
        Map<String, Object> res = base.post("/payments/" + id + "/splits", params, opts);
        return Payment.parsePayment(Map.of("transaction", HttpUtils.pickResource(res, "transaction")));
    }

    public ListResult listRefunds(String id, Map<String, Object> params) throws WajubException {
        Map<String, Object> res = base.get("/payments/" + id + "/refunds", params);
        return HttpUtils.pickList(res, "refunds");
    }
}
