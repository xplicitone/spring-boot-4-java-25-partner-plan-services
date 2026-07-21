# EventBridge Migration — What Changes, What to Ask

Status: **planned, not adopted.** `main` currently uses the SNS-based event backbone
(commit that this doc is added in and earlier). Keep building and running against SNS;
adopt the changes below only once the org EventBridge module and the publishing team's
bus are ready. If EventBridge is not ready, **no action is needed — the current SNS
version is complete and self-contained.**

## Context

Architects are standardizing internal eventing on EventBridge:

- Each **publishing team** owns a custom EventBridge bus **in their own AWS account**.
- Applications publish events to their own bus.
- **Rules + targets** route events to subscribers (SQS, SNS, Lambda, …).
- Subscribers request an event by standing up a target and submitting an **MR** to the
  publishing team with a rule that points at that target.
- Reusable Terraform modules already exist for the **bus** and for **rules**.

Separately, partner-facing APIs may move to **Mulesoft Flex Gateway** (API management),
which is only relevant to our Inbox API edge, not to any of the eventing.

## Why this fits our design

Our delivery pipeline is deliberately layered so the transport can change without
touching the core:

1. How the approval event reaches the delivery service — **this is the only thing
   EventBridge changes.**
2. The delivery service's durable `Delivery` record, idempotency, retry — **unchanged.**
3. How each partner receives it (per-partner SQS queue, or the Inbox API) — **unchanged.**
   The non-AWS partner contract in `partner-integration-non-aws.md` is unaffected.

EventBridge replaces the internal SNS topic + subscription. Everything downstream of
the `partner-delivery-inbound` SQS queue stays exactly as-is.

## What actually changes

### 1. Application service (publisher) — `application-service-outbox-example`

Today: the outbox externalizes `ApplicationApproved` to an **SNS topic** via
`spring-cloud-aws-modulith-events-sns`.

After: the outbox publishes to the team's **EventBridge bus** via `PutEvents`.

- The transactional-outbox guarantee is **unchanged** — the event still commits in the
  same transaction as the approval (Spring Modulith event publication registry), and is
  only published after commit, with re-publish on restart.
- **OPEN QUESTION (verify before building):** does a Spring Cloud AWS *EventBridge*
  Modulith externalizer exist (equivalent to the SNS one)? If yes, it's a dependency +
  config swap. If no, the outbox completion step does a plain `PutEvents` call instead
  of using a drop-in externalizer. Either way the guarantee holds; only the publish
  mechanism differs.
- The event stays a **claim-check**: `{applicationId, partnerId, eventType, occurredAt}`,
  no account payload (EventBridge caps ~256KB and other teams' rules can match our
  events — PII on the bus is worse here than on SNS).

### 2. Infra — `modules/event-backbone`

Today: SNS topic + SQS queue + DLQ + SNS→SQS subscription.

After:
- **Remove** the SNS topic and the `aws_sns_topic_subscription`.
- **Keep** `partner-delivery-inbound` queue + DLQ, and the queue policy — but the policy
  principal changes from `sns.amazonaws.com` to `events.amazonaws.com`, scoped by the
  rule ARN via `aws:SourceArn`.
- **Add** an EventBridge **rule** (using the org's reusable rules module) that
  pattern-matches `ApplicationApproved` and targets our queue. This rule lives in the
  separate Terraform file the MR process expects, and is submitted as an **MR to the
  publishing (application) team**, not applied by us.
- `modules/partner-channel` (per-partner AWS partner queues) is **untouched** — partners
  still consume a queue, not a bus. EventBridge doesn't buffer/retry with backpressure
  the way SQS does, so the partner-facing artifact stays a queue.

### 3. Delivery service — `partner-delivery-service`

**No change.** `@SqsListener` on `partner-delivery-inbound` does not care whether the
message arrived via an SNS subscription or an EventBridge rule. Verify only that the
event JSON EventBridge delivers deserializes into `ApplicationApprovedEvent` (field
names match; confirm whether the target is configured with an input transformer or
delivers the raw `detail`).

### 4. Inbox API edge (separate from EventBridge) — Mulesoft Flex Gateway

Only relevant if partner APIs move to Flex Gateway. `InboxController` is unchanged — it
trusts whatever partner-identity header the edge injects. What changes is *where* the
OAuth2 JWT is validated and `X-Partner-Id` is stamped: today Istio
(`k8s/istio-inbox.yaml`); potentially Flex Gateway instead. See open questions.

## What to ask the architects / integration-layer team

Bring these to the meeting:

1. **`PutEvents` contract for the bus** — required `source`, `detail-type`, `resources`,
   and the `detail` schema convention. Determines how the outbox publishes.
2. **Encryption / KMS expectations** on the bus and on cross-account rule targets.
3. **Rules module conventions** — inputs, naming, tagging — so our subscription-rule MR
   matches their standard on the first submission.
4. **Target payload shape** — does the rule deliver the raw `detail`, or must we use an
   input transformer to match `ApplicationApprovedEvent`? (Drives item 3 above.)
5. **EventBridge externalizer availability** — is there a Spring Cloud AWS Modulith
   EventBridge externalizer, or do we hand-roll `PutEvents` in the outbox completion?
6. **Flex Gateway vs Istio** — does Flex Gateway *replace* the Istio JWT layer as the
   north-south partner edge, or *front* it (partner → Flex Gateway → Istio ingress →
   service)? Determines whether `k8s/istio-inbox.yaml` is deleted or just loses its
   JWT-validation responsibility.

Points to raise **from us** in the meeting:

- Our per-partner delivery to AWS partners is intentionally **SQS, not EventBridge** —
  align so nobody expects us to hand partners a bus.
- The **claim-check** constraint (no account data on the bus).

## Rollback / not-ready path

There is nothing to roll back — this doc describes a *future* swap. Until EventBridge is
adopted, stay on the current commit. The SNS event backbone, the outbox example, the
delivery service, and the partner Inbox API are all complete and work end-to-end without
any EventBridge dependency. Adopt the changes above as a single coordinated change (app
team outbox + our infra MR) once items 1–5 are answered.
