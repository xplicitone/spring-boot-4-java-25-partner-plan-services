package com.partnerplan.delivery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param inboundQueue        name of the internal SQS queue subscribed to the ApplicationApproved SNS topic
 * @param accountFetchBaseUrl base URL partners use to fetch full account details (claim-check pattern);
 *                            the queue/inbox message only carries a pointer, never the account data itself
 */
@ConfigurationProperties(prefix = "delivery")
public record DeliveryProperties(String inboundQueue, String accountFetchBaseUrl) {
}
