package com.wajub;

import com.wajub.exception.WajubException;

import java.util.Map;

public class BalanceResource {

    private final BaseClient base;

    BalanceResource(BaseClient base) {
        this.base = base;
    }

    public Map<String, Object> retrieve() throws WajubException {
        Map<String, Object> res = base.get("/balance", null);
        return HttpUtils.pickResource(res, "balance", "data");
    }
}
