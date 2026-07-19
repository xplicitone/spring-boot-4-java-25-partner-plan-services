package com.partnerplan.delivery.domain;

import java.util.Set;

/**
 * Lifecycle of a delivery to a partner.
 *
 * PENDING      created from an ApplicationApproved event, not yet handed to a channel
 * DISPATCHED   put on the partner's SQS queue, or made visible in their inbox
 * ACKNOWLEDGED partner confirmed receipt (inbox ack, or optional ack call for SQS partners)
 * FAILED       channel dispatch failed; eligible for retry
 * EXPIRED      partner never picked it up within the retention window; replay creates a new delivery
 */
public enum DeliveryStatus {
    PENDING,
    DISPATCHED,
    ACKNOWLEDGED,
    FAILED,
    EXPIRED;

    private static final Set<DeliveryStatus> TERMINAL = Set.of(ACKNOWLEDGED, EXPIRED);

    public boolean canTransitionTo(DeliveryStatus target) {
        if (TERMINAL.contains(this)) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == DISPATCHED || target == FAILED || target == EXPIRED;
            case DISPATCHED -> target == ACKNOWLEDGED || target == EXPIRED || target == FAILED;
            case FAILED -> target == DISPATCHED || target == EXPIRED;
            case ACKNOWLEDGED, EXPIRED -> false;
        };
    }
}
