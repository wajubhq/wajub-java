package com.wajub;

import com.wajub.exception.WajubException;

import java.util.Map;

public class AccountsResource extends CrudResource {

    AccountsResource(BaseClient base) {
        super(base, "accounts", "account", "accounts");
    }

    public Map<String, Object> regenerateToken(String id, RequestOptions opts) throws WajubException {
        Map<String, Object> res = base.post("/accounts/" + id + "/token", null, opts);
        return HttpUtils.pickResource(res, "account");
    }
}
