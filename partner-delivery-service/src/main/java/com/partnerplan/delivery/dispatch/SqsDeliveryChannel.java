package com.partnerplan.delivery.dispatch;

import com.partnerplan.delivery.domain.ChannelType;
import com.partnerplan.delivery.domain.Delivery;
import com.partnerplan.delivery.partner.PartnerChannelConfig;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends the message to the partner's dedicated queue in our account.
 * The partner consumes it cross-account via their own IAM role; delivery is
 * at-least-once, so partners dedupe on {@code deliveryId}.
 */
@Component
public class SqsDeliveryChannel implements DeliveryChannel {

    private final SqsTemplate sqsTemplate;

    public SqsDeliveryChannel(SqsTemplate sqsTemplate) {
        this.sqsTemplate = sqsTemplate;
    }

    @Override
    public ChannelType type() {
        return ChannelType.SQS;
    }

    @Override
    public void dispatch(Delivery delivery, DeliveryMessage message, PartnerChannelConfig config) {
        sqsTemplate.send(to -> to
                .queue(config.getSqsQueueUrl())
                .header("delivery-id", delivery.getId().toString())
                .payload(message));
    }
}
