# Partner Integration Guide — Inbox API (non-AWS partners)

This guide is for partners who do **not** have an AWS account. Instead of us calling
your systems when an application is approved, **you poll our Inbox API** on your own
schedule, fetch the account details, and acknowledge each delivery. You need nothing
but an HTTPS client and a scheduler (cron, Quartz, Sidekiq, systemd timer — anything).

If you *do* have an AWS account, ask us about the SQS integration instead — you'll
consume a dedicated queue with your own IAM role and skip the polling loop.

## What you need from us (one-time onboarding)

1. **OAuth2 client credentials** — a `client_id` and `client_secret` for our identity
   provider. Your `client_id` is your partner identity; every delivery you can see is
   scoped to it. Store the secret in your secrets manager; rotate on our published cadence.
2. **Base URL** — `https://partners.example.com` (environment-specific URLs provided
   at onboarding).
3. **Token URL** — `https://auth.example.com/oauth2/token`.

## The loop you implement

Run this on a schedule — **every 1 to 5 minutes** is typical. There is no penalty for
polling when the inbox is empty.

### Step 1 — Get an access token (client credentials grant)

```bash
curl -s -X POST https://auth.example.com/oauth2/token \
  -d grant_type=client_credentials \
  -d client_id=$CLIENT_ID \
  -d client_secret=$CLIENT_SECRET
```

Cache the token until it expires (`expires_in`); do not request a new token per call.

### Step 2 — Poll for pending deliveries

```bash
curl -s https://partners.example.com/inbox/deliveries \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Response — every delivery owed to you that you have not yet acknowledged, oldest first:

```json
[
  {
    "deliveryId": "0f8c6b1e-3f5a-4b7e-9c2d-8a1b2c3d4e5f",
    "applicationId": "app-20260719-00042",
    "eventType": "ApplicationApproved",
    "status": "DISPATCHED",
    "createdAt": "2026-07-19T14:03:11Z",
    "accountFetchUrl": "https://partners.example.com/api/applications/app-20260719-00042/account"
  }
]
```

An empty array `[]` means nothing is waiting. That is the normal case.

### Step 3 — Fetch the account details

The delivery is a notification, not the data. Fetch the account information with the
same bearer token:

```bash
curl -s "$ACCOUNT_FETCH_URL" -H "Authorization: Bearer $ACCESS_TOKEN"
```

Persist the result in your own systems **before** step 4.

### Step 4 — Acknowledge the delivery

```bash
curl -s -X POST \
  https://partners.example.com/inbox/deliveries/0f8c6b1e-3f5a-4b7e-9c2d-8a1b2c3d4e5f/ack \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Returns the delivery with `"status": "ACKNOWLEDGED"`. Acknowledged deliveries stop
appearing in step 2.

**Ack only after you have durably stored the account data.** If you crash between
fetch and ack, the delivery simply reappears on your next poll — that is the safety
mechanism, not a bug.

### Reference pseudocode

```text
every N minutes:
    token = cached_or_new_oauth_token()
    for delivery in GET /inbox/deliveries:
        if already_processed(delivery.deliveryId):        # dedupe guard
            POST /inbox/deliveries/{deliveryId}/ack
            continue
        account = GET delivery.accountFetchUrl
        save_to_your_database(delivery.deliveryId, account)   # durable, idempotent
        POST /inbox/deliveries/{deliveryId}/ack
```

## Rules of the contract

- **At-least-once delivery.** You may occasionally see a delivery you already
  processed (e.g. you acked but the response was lost). Keep the `deliveryId` of
  everything you've processed and skip duplicates. `deliveryId` is globally unique
  and stable across retries.
- **Order is best-effort.** Deliveries are returned oldest-first, but do not build
  logic that assumes strict ordering across applications.
- **Poll ≥ once per day, ideally every few minutes.** Deliveries you never
  acknowledge are flagged on our side after 24 hours and eventually expire; our
  operations team will contact you, but timely pickup is your responsibility under
  the integration agreement.
- **Acking is per delivery.** There is no bulk ack; loop over each item.
- **Never share tokens across environments.** Sandbox and production have separate
  credentials and base URLs.

## HTTP status codes

| Code | Meaning | What you should do |
|---|---|---|
| 200 | Success | Continue |
| 400 | Malformed request (e.g. bad UUID) | Fix the request; do not retry as-is |
| 401 | Missing/expired/invalid token | Refresh the token, retry once |
| 403 | Token valid but not permitted | Contact us — likely a credential misconfiguration |
| 404 | Unknown `deliveryId` (or not yours) | Treat as already handled; investigate if frequent |
| 429 | You are polling too aggressively | Back off; respect `Retry-After` |
| 5xx | Our side | Retry with exponential backoff (start 30s, cap 10min) |

## Sandbox onboarding checklist

1. Receive sandbox credentials and base URL.
2. Implement the loop above against sandbox; we will inject test approvals on request.
3. Demonstrate: token caching, empty-inbox polling, fetch-then-ack ordering, and
   duplicate handling (we will redeliver one item deliberately).
4. Receive production credentials. During the migration window we dual-run: you keep
   receiving today's REST callbacks *and* inbox deliveries until you confirm cutover,
   then we disable the callback.

## Support

- Integration questions: partner-integrations@example.com
- Report a suspected missed delivery with the `applicationId`; we keep a durable
  record of every delivery and its acknowledgement timestamps and can replay any of
  them into your inbox.
