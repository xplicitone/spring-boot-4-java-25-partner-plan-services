package com.partnerplan.application;

import java.time.Instant;
import org.springframework.modulith.events.Externalized;

/**
 * Domain event raised when an application is approved. {@code @Externalized} marks it
 * for publication outside the process; the actual SNS destination is resolved in
 * {@link EventExternalizationConfig} so the env-prefixed topic name stays in config.
 *
 * Field names match the delivery service's ApplicationApprovedEvent — this JSON is
 * exactly what arrives on the partner-delivery-inbound queue (raw message delivery).
 */
@Externalized
public record ApplicationApproved(
        String applicationId,
        String partnerId,
        String eventType,
        Instant approvedAt) {

    public static ApplicationApproved of(PartnerApplication application) {
        return new ApplicationApproved(
                application.getId(),
                application.getPartnerId(),
                "ApplicationApproved",
                application.getApprovedAt());
    }
}
