package com.partnerplan.delivery.inbound;

import com.partnerplan.delivery.dispatch.DeliveryService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.stereotype.Component;

/**
 * Consumes ApplicationApproved events from the internal queue (SNS topic -> this queue,
 * wired in the infra repo). Failures are retried by SQS visibility timeout and land on
 * the internal DLQ after maxReceiveCount.
 */
@Component
public class ApplicationApprovedListener {

    private final DeliveryService deliveryService;

    public ApplicationApprovedListener(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @SqsListener("${delivery.inbound-queue}")
    public void onApplicationApproved(ApplicationApprovedEvent event) {
        deliveryService.process(event);
    }
}
