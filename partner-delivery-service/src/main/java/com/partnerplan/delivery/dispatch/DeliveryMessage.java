package com.partnerplan.delivery.dispatch;

import java.time.Instant;
import java.util.UUID;

/**
 * The envelope partners actually receive (on their queue or from the inbox).
 * Claim-check pattern: it carries a pointer ({@code accountFetchUrl}), never account data,
 * so nothing sensitive sits in SQS and queue retention limits can't lose data.
 */
public record DeliveryMessage(
        UUID deliveryId,
        String applicationId,
        String eventType,
        Instant occurredAt,
        String accountFetchUrl) {
}
