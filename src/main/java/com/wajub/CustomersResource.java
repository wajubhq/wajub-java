package com.wajub;

import com.wajub.exception.WajubException;

import java.util.Map;

public class CustomersResource extends CrudResource {

    CustomersResource(BaseClient base) {
        super(base, "customers", "customer", "customers");
    }

    public Map<String, Object> block(String id, Map<String, Object> params, RequestOptions opts)
            throws WajubException {
        Map<String, Object> res = base.post("/customers/" + id + "/block", params, opts);
        return HttpUtils.pickResource(res, "customer");
    }

    public Map<String, Object> unblock(String id, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.post("/customers/" + id + "/unblock", null, opts);
        return HttpUtils.pickResource(res, "customer");
    }

    public Map<String, Object> activate(String id, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.post("/customers/" + id + "/activate", null, opts);
        return HttpUtils.pickResource(res, "customer");
    }

    public Map<String, Object> deactivate(String id, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.post("/customers/" + id + "/deactivate", null, opts);
        return HttpUtils.pickResource(res, "customer");
    }

    public ListResult listTaxIds(String customerId) throws WajubException {
        Map<String, Object> res = base.get("/customers/" + customerId + "/tax_ids", null);
        return HttpUtils.pickList(res, "tax_ids");
    }

    public Map<String, Object> createTaxId(String customerId, Map<String, Object> params, RequestOptions opts)
            throws WajubException {
        Map<String, Object> res = base.post("/customers/" + customerId + "/tax_ids", params, opts);
        return HttpUtils.pickResource(res, "tax_id");
    }

    public void deleteTaxId(String customerId, String taxId, RequestOptions opts) throws WajubException {
        base.delete("/customers/" + customerId + "/tax_ids/" + taxId, opts);
    }
}
