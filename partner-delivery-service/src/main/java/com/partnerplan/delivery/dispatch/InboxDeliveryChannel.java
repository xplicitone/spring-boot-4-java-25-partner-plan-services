package com.partnerplan.delivery.dispatch;

import com.partnerplan.delivery.domain.ChannelType;
import com.partnerplan.delivery.domain.Delivery;
import com.partnerplan.delivery.partner.PartnerChannelConfig;
import org.springframework.stereotype.Component;

/**
 * Inbox partners pull from the Inbox API, so "dispatching" is just making the
 * delivery visible — the DISPATCHED row itself is what the partner polls for.
 */
@Component
public class InboxDeliveryChannel implements DeliveryChannel {

    @Override
    public ChannelType type() {
        return ChannelType.INBOX;
    }

    @Override
    public void dispatch(Delivery delivery, DeliveryMessage message, PartnerChannelConfig config) {
        // No outbound call: visibility in the inbox is driven by the delivery's status.
    }
}
