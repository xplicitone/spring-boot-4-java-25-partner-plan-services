package com.partnerplan.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Minimal stand-in for the real application aggregate in your application service. */
@Entity
@Table(name = "partner_application")
public class PartnerApplication {

    public enum Status { SUBMITTED, APPROVED, REJECTED }

    @Id
    private String id;

    @Column(name = "partner_id", nullable = false)
    private String partnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected PartnerApplication() {
    }

    public PartnerApplication(String id, String partnerId) {
        this.id = id;
        this.partnerId = partnerId;
        this.status = Status.SUBMITTED;
    }

    public boolean approve() {
        if (status != Status.SUBMITTED) {
            return false;
        }
        this.status = Status.APPROVED;
        this.approvedAt = Instant.now();
        return true;
    }

    public String getId() {
        return id;
    }

    public String getPartnerId() {
        return partnerId;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }
}
