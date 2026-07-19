package com.partnerplan.delivery.domain;

public enum ChannelType {
    /** Partner consumes a dedicated cross-account SQS queue in our AWS account. */
    SQS,
    /** Partner polls our Inbox API (non-AWS partners). */
    INBOX,
    /** Outbound REST push — migration path only, for partners not yet moved off the old flow. */
    LEGACY_REST
}
