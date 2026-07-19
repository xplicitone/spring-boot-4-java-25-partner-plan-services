package com.partnerplan.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Durable record of one event owed to one partner. This table is the source of truth
 * for replay and dispute resolution — the queue/inbox message is only a pointer to it.
 */
@Entity
@Table(name = "delivery")
public class Delivery {

    @Id
    private UUID id;

    @Column(name = "partner_id", nullable = false)
    private String partnerId;

    @Column(name = "application_id", nullable = false)
    private String applicationId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChannelType channel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Version
    private long version;

    protected Delivery() {
    }

    public Delivery(String partnerId, String applicationId, String eventType, ChannelType channel) {
        this.id = UUID.randomUUID();
        this.partnerId = partnerId;
        this.applicationId = applicationId;
        this.eventType = eventType;
        this.channel = channel;
        this.status = DeliveryStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void markDispatched() {
        transitionTo(DeliveryStatus.DISPATCHED);
        this.dispatchedAt = Instant.now();
        this.attempts++;
    }

    public void markAcknowledged() {
        transitionTo(DeliveryStatus.ACKNOWLEDGED);
        this.acknowledgedAt = Instant.now();
    }

    public void markFailed(String error) {
        transitionTo(DeliveryStatus.FAILED);
        this.lastError = error;
        this.attempts++;
    }

    public void markExpired() {
        transitionTo(DeliveryStatus.EXPIRED);
    }

    private void transitionTo(DeliveryStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Delivery %s cannot transition from %s to %s".formatted(id, status, target));
        }
        this.status = target;
    }

    public UUID getId() {
        return id;
    }

    public String getPartnerId() {
        return partnerId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getEventType() {
        return eventType;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public ChannelType getChannel() {
        return channel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
