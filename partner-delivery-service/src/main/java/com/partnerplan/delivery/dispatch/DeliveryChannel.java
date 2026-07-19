package com.partnerplan.delivery.dispatch;

import com.partnerplan.delivery.domain.ChannelType;
import com.partnerplan.delivery.domain.Delivery;
import com.partnerplan.delivery.partner.PartnerChannelConfig;

public interface DeliveryChannel {

    ChannelType type();

    void dispatch(Delivery delivery, DeliveryMessage message, PartnerChannelConfig config);
}
