package com.partnerplan.delivery.dispatch;

import com.partnerplan.delivery.domain.ChannelType;
import com.partnerplan.delivery.domain.Delivery;
import com.partnerplan.delivery.partner.PartnerChannelConfig;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Old outbound REST push, kept as a channel so partners migrate one config flip
 * at a time instead of in a big bang. Remove once the last partner is off it.
 */
@Component
public class LegacyRestDeliveryChannel implements DeliveryChannel {

    private final RestClient restClient;

    public LegacyRestDeliveryChannel(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Override
    public ChannelType type() {
        return ChannelType.LEGACY_REST;
    }

    @Override
    public void dispatch(Delivery delivery, DeliveryMessage message, PartnerChannelConfig config) {
        restClient.post()
                .uri(config.getLegacyEndpoint())
                .contentType(MediaType.APPLICATION_JSON)
                .body(message)
                .retrieve()
                .toBodilessEntity();
    }
}
