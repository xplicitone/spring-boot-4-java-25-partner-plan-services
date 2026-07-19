package com.partnerplan.delivery.inbound;

import java.time.Instant;

/**
 * Domain event published (via transactional outbox) by the application service
 * when a partner-submitted application is approved.
 */
public record ApplicationApprovedEvent(
        String applicationId,
        String partnerId,
        String eventType,
        Instant approvedAt) {
}
