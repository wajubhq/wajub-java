package com.wajub;

import okhttp3.OkHttpClient;

/**
 * The Wajub server SDK entry point.
 */
public class Wajub {

    private final GlobalResource global;
    private final PaymentsResource payments;
    private final CustomersResource customers;
    private final CrudResource refunds;
    private final CrudResource transfers;
    private final CrudResource beneficiaries;
    private final CrudResource links;
    private final BalanceResource balance;
    private final EventsResource events;
    private final AccountsResource accounts;
    private final WebhookEndpointsResource webhookEndpoints;
    private final InvoicesResource invoices;
    private final DisputesResource disputes;
    private final IdentityResource identity;
    private final TaxResource tax;
    private final ShieldResource shield;
    private final ListenResource listen;
    private final Webhooks webhooks;

    private Wajub(Config config) {
        String apiKey = HttpUtils.normalizeApiKey(config.apiKey);
        if (apiKey.isBlank()) {
            apiKey = HttpUtils.normalizeApiKey(System.getenv("WAJUB_API_KEY"));
        }
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("wajub: api key is required");
        }

        String baseUrl = HttpUtils.defaultApiUrl();

        String prefix = config.idempotencyKeyPrefix;
        if (prefix == null || prefix.isBlank()) {
            prefix = "wajub";
        }

        BaseClient base = new BaseClient(apiKey, baseUrl, prefix, config.httpClient);

        String whSecret = config.webhookSecret;
        if (whSecret == null || whSecret.isBlank()) {
            whSecret = System.getenv("WAJUB_WEBHOOK_SECRET");
        }

        this.global = new GlobalResource(base);
        this.payments = new PaymentsResource(base);
        this.customers = new CustomersResource(base);
        this.refunds = new CrudResource(base, "refunds", "refund", "refunds");
        this.transfers = new CrudResource(base, "transfers", "transfer", "transfers");
        this.beneficiaries = new CrudResource(base, "beneficiaries", "beneficiary", "beneficiaries");
        this.links = new CrudResource(base, "links", "link", "links");
        this.balance = new BalanceResource(base);
        this.events = new EventsResource(base);
        this.accounts = new AccountsResource(base);
        this.webhookEndpoints = new WebhookEndpointsResource(base);
        this.invoices = new InvoicesResource(base);
        this.disputes = new DisputesResource(base);
        this.identity = new IdentityResource(base);
        this.tax = new TaxResource(base);
        this.shield = new ShieldResource(base);
        this.listen = new ListenResource(base);
        this.webhooks = new Webhooks(whSecret);
    }

    /** Creates a Wajub API client. */
    public static Wajub create(Config config) {
        return new Wajub(config);
    }

    /** Creates a Wajub API client with the given API key. */
    public static Wajub create(String apiKey) {
        return new Wajub(Config.builder().apiKey(apiKey).build());
    }

    public GlobalResource global() {
        return global;
    }

    public PaymentsResource payments() {
        return payments;
    }

    public CustomersResource customers() {
        return customers;
    }

    public CrudResource refunds() {
        return refunds;
    }

    public CrudResource transfers() {
        return transfers;
    }

    public CrudResource beneficiaries() {
        return beneficiaries;
    }

    public CrudResource links() {
        return links;
    }

    public BalanceResource balance() {
        return balance;
    }

    public EventsResource events() {
        return events;
    }

    public AccountsResource accounts() {
        return accounts;
    }

    public WebhookEndpointsResource webhookEndpoints() {
        return webhookEndpoints;
    }

    public InvoicesResource invoices() {
        return invoices;
    }

    public DisputesResource disputes() {
        return disputes;
    }

    public IdentityResource identity() {
        return identity;
    }

    public TaxResource tax() {
        return tax;
    }

    public ShieldResource shield() {
        return shield;
    }

    public ListenResource listen() {
        return listen;
    }

    public Webhooks webhooks() {
        return webhooks;
    }

    /** Configures a new Wajub client. */
    public static final class Config {

        private String apiKey;
        private String webhookSecret;
        private String idempotencyKeyPrefix;
        private OkHttpClient httpClient;

        private Config() {}

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {

            private final Config config = new Config();

            public Builder apiKey(String apiKey) {
                config.apiKey = apiKey;
                return this;
            }

            public Builder webhookSecret(String webhookSecret) {
                config.webhookSecret = webhookSecret;
                return this;
            }

            public Builder idempotencyKeyPrefix(String prefix) {
                config.idempotencyKeyPrefix = prefix;
                return this;
            }

            public Builder httpClient(OkHttpClient httpClient) {
                config.httpClient = httpClient;
                return this;
            }

            public Config build() {
                return config;
            }
        }
    }
}
