package com.partnerplan.delivery.partner;

import com.partnerplan.delivery.domain.ChannelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Per-partner delivery channel onboarding. Flipping {@code channelType} is how a partner
 * migrates from LEGACY_REST to SQS or INBOX.
 */
@Entity
@Table(name = "partner_channel_config")
public class PartnerChannelConfig {

    @Id
    @Column(name = "partner_id")
    private String partnerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false)
    private ChannelType channelType;

    /** URL of the partner's dedicated cross-account queue in our account (SQS channel only). */
    @Column(name = "sqs_queue_url")
    private String sqsQueueUrl;

    /** Outbound endpoint for partners still on the old REST push (LEGACY_REST only). */
    @Column(name = "legacy_endpoint")
    private String legacyEndpoint;

    @Column(nullable = false)
    private boolean active;

    protected PartnerChannelConfig() {
    }

    public PartnerChannelConfig(String partnerId, ChannelType channelType, String sqsQueueUrl,
                                String legacyEndpoint, boolean active) {
        this.partnerId = partnerId;
        this.channelType = channelType;
        this.sqsQueueUrl = sqsQueueUrl;
        this.legacyEndpoint = legacyEndpoint;
        this.active = active;
    }

    public String getPartnerId() {
        return partnerId;
    }

    public ChannelType getChannelType() {
        return channelType;
    }

    public String getSqsQueueUrl() {
        return sqsQueueUrl;
    }

    public String getLegacyEndpoint() {
        return legacyEndpoint;
    }

    public boolean isActive() {
        return active;
    }
}
