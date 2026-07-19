# spring-boot-4-java-25-partner-plan-services

Services for pull-based partner delivery of approved-application account information.
Java 25, Spring Boot 4.1, Spring Cloud AWS 4.

## Architecture

```
application service ──(transactional outbox)──▶ SNS: application-events
                                                     │
                                                     ▼
                                    SQS: partner-delivery-inbound (+ DLQ)
                                                     │
                                                     ▼
                                        partner-delivery-service
                                          │ creates durable Delivery record
                                          │ routes by PartnerChannelConfig
                          ┌───────────────┼───────────────────┐
                          ▼               ▼                   ▼
                 SQS channel         INBOX channel       LEGACY_REST channel
            per-partner queue in    partner polls        old outbound push,
            our account, consumed   GET /inbox/…, then   kept for migration
            cross-account by the    POST …/ack           only
            partner's IAM role
```

Key decisions:

- **Pull, not push.** AWS partners consume a dedicated cross-account SQS queue; non-AWS
  partners poll the Inbox API. We never call into partner infrastructure.
- **Claim-check.** Messages carry `accountFetchUrl`, never account data. No PII on queues,
  and SQS's 14-day retention can only lose a notification, not the data.
- **Durable delivery record.** The `delivery` table is the source of truth (replay,
  disputes, expiry). State machine: PENDING → DISPATCHED → ACKNOWLEDGED, with
  FAILED (retryable) and EXPIRED.
- **At-least-once everywhere.** Inbound events are deduped on
  (partner, application, eventType); partners dedupe on `deliveryId`.
- **Migration by config flip.** `partner_channel_config.channel_type` moves a partner
  from LEGACY_REST to SQS/INBOX one at a time; dual-run by keeping LEGACY_REST until
  the partner confirms consumption.

## Modules

- `application-service-outbox-example/` — how the existing application service publishes
  ApplicationApproved to SNS via a transactional outbox (Spring Modulith event
  publication registry + the Spring Cloud AWS SNS externalizer). The approval row and
  the outbox row commit in one transaction; SNS publish happens after commit and is
  re-driven on restart if it fails.
- `partner-delivery-service/` — the delivery router, Inbox API, and channel implementations.
  - `k8s/` — Deployment (IRSA service account) and Istio edge config: the ingress gateway
    validates the partner's OAuth2 JWT and stamps the validated `client_id` into
    `X-Partner-Id`; the service trusts that header only from the mesh.

Infra (SNS topic, internal queue, per-partner queues, IAM) lives in
`spring-boot-4-java-25-partner-plan-infra`.

## Build

```
cd partner-delivery-service
mvn test          # unit tests, no AWS or DB needed
mvn spring-boot:run   # needs Postgres + AWS creds (see application.yaml env vars)
```
