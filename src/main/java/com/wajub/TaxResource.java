package com.wajub;

import com.wajub.exception.WajubException;

import java.util.Map;

public class TaxResource {

    private final BaseClient base;

    TaxResource(BaseClient base) {
        this.base = base;
    }

    public Map<String, Object> getSettings() throws WajubException {
        Map<String, Object> res = base.get("/tax/settings", null);
        return HttpUtils.pickResource(res, "tax", "settings");
    }

    public Map<String, Object> updateSettings(Map<String, Object> params, RequestOptions opts)
            throws WajubException {
        Map<String, Object> res = base.put("/tax/settings", params, opts);
        return HttpUtils.pickResource(res, "tax", "settings");
    }

    public ListResult rates(Map<String, Object> params) throws WajubException {
        Map<String, Object> res = base.get("/tax/rates", params);
        return HttpUtils.pickList(res, "rates", "tax_rates");
    }

    public Map<String, Object> calculate(Map<String, Object> params, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.post("/tax/calculate", params, opts);
        return HttpUtils.pickResource(res, "tax", "calculation");
    }

    public ListResult reports(Map<String, Object> params) throws WajubException {
        Map<String, Object> res = base.get("/tax/reports", params);
        return HttpUtils.pickList(res, "reports", "tax_reports");
    }

    public ListResult listCodes(Map<String, Object> params) throws WajubException {
        Map<String, Object> res = base.get("/tax/codes", params);
        return HttpUtils.pickList(res, "tax_codes", "codes");
    }

    public Map<String, Object> retrieveCode(String code) throws WajubException {
        Map<String, Object> res = base.get("/tax/codes/" + code, null);
        return HttpUtils.pickResource(res, "tax_code", "code");
    }

    public PagedResult listRegistrations(Map<String, Object> params) throws WajubException {
        return PagedResult.createPagedList(base, "/tax/registrations", "registrations", params);
    }

    public Map<String, Object> createRegistration(Map<String, Object> params, RequestOptions opts)
            throws WajubException {
        Map<String, Object> res = base.post("/tax/registrations", params, opts);
        return HttpUtils.pickResource(res, "registration", "tax_registration");
    }

    public Map<String, Object> retrieveRegistration(String id) throws WajubException {
        Map<String, Object> res = base.get("/tax/registrations/" + id, null);
        return HttpUtils.pickResource(res, "registration", "tax_registration");
    }

    public Map<String, Object> updateRegistration(String id, Map<String, Object> params, RequestOptions opts)
            throws WajubException {
        Map<String, Object> res = base.put("/tax/registrations/" + id, params, opts);
        return HttpUtils.pickResource(res, "registration", "tax_registration");
    }

    public void deleteRegistration(String id, RequestOptions opts) throws WajubException {
        base.delete("/tax/registrations/" + id, opts);
    }

    public ListResult jurisdictions(Map<String, Object> params) throws WajubException {
        Map<String, Object> res = base.get("/tax/jurisdictions", params);
        return HttpUtils.pickList(res, "jurisdictions", "tax_jurisdictions");
    }

    public ListResult thresholds(Map<String, Object> params) throws WajubException {
        Map<String, Object> res = base.get("/tax/thresholds", params);
        return HttpUtils.pickList(res, "thresholds", "tax_thresholds");
    }

    public ListResult thresholdAlerts(Map<String, Object> params) throws WajubException {
        Map<String, Object> res = base.get("/tax/thresholds/alerts", params);
        return HttpUtils.pickList(res, "alerts", "threshold_alerts");
    }
}
