package com.partnerplan.delivery.dispatch;

import com.partnerplan.delivery.config.DeliveryProperties;
import com.partnerplan.delivery.domain.Delivery;
import com.partnerplan.delivery.domain.DeliveryRepository;
import com.partnerplan.delivery.domain.DeliveryStatus;
import com.partnerplan.delivery.inbound.ApplicationApprovedEvent;
import com.partnerplan.delivery.partner.PartnerChannelConfig;
import com.partnerplan.delivery.partner.PartnerChannelConfigRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final DeliveryRepository deliveries;
    private final PartnerChannelConfigRepository partnerConfigs;
    private final Map<com.partnerplan.delivery.domain.ChannelType, DeliveryChannel> channels;
    private final DeliveryProperties properties;

    public DeliveryService(DeliveryRepository deliveries,
                           PartnerChannelConfigRepository partnerConfigs,
                           List<DeliveryChannel> channelList,
                           DeliveryProperties properties) {
        this.deliveries = deliveries;
        this.partnerConfigs = partnerConfigs;
        this.channels = channelList.stream().collect(Collectors.toMap(
                DeliveryChannel::type, Function.identity(),
                (a, b) -> a, () -> new EnumMap<>(com.partnerplan.delivery.domain.ChannelType.class)));
        this.properties = properties;
    }

    /**
     * Creates the durable delivery record and dispatches it to the partner's channel.
     * Idempotent per (partner, application, eventType): SQS is at-least-once, so the
     * inbound event may arrive more than once.
     */
    @Transactional
    public void process(ApplicationApprovedEvent event) {
        if (deliveries.existsByPartnerIdAndApplicationIdAndEventType(
                event.partnerId(), event.applicationId(), event.eventType())) {
            log.info("Duplicate event ignored: partner={} application={} type={}",
                    event.partnerId(), event.applicationId(), event.eventType());
            return;
        }

        PartnerChannelConfig config = partnerConfigs.findById(event.partnerId())
                .filter(PartnerChannelConfig::isActive)
                .orElseThrow(() -> new IllegalStateException(
                        "No active channel config for partner " + event.partnerId()));

        Delivery delivery = new Delivery(
                event.partnerId(), event.applicationId(), event.eventType(), config.getChannelType());
        deliveries.save(delivery);
        dispatch(delivery, config, event.approvedAt());
    }

    /** Retries a FAILED delivery, or replays one on demand. */
    @Transactional
    public void redispatch(UUID deliveryId) {
        Delivery delivery = deliveries.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown delivery " + deliveryId));
        PartnerChannelConfig config = partnerConfigs.findById(delivery.getPartnerId())
                .orElseThrow(() -> new IllegalStateException(
                        "No channel config for partner " + delivery.getPartnerId()));
        dispatch(delivery, config, delivery.getCreatedAt());
    }

    private void dispatch(Delivery delivery, PartnerChannelConfig config, Instant occurredAt) {
        DeliveryChannel channel = channels.get(config.getChannelType());
        if (channel == null) {
            throw new IllegalStateException("No channel implementation for " + config.getChannelType());
        }
        DeliveryMessage message = new DeliveryMessage(
                delivery.getId(),
                delivery.getApplicationId(),
                delivery.getEventType(),
                occurredAt,
                properties.accountFetchBaseUrl() + "/applications/" + delivery.getApplicationId() + "/account");
        try {
            channel.dispatch(delivery, message, config);
            delivery.markDispatched();
        } catch (RuntimeException e) {
            log.error("Dispatch failed for delivery {} via {}", delivery.getId(), config.getChannelType(), e);
            delivery.markFailed(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Delivery> pendingForPartner(String partnerId) {
        return deliveries.findByPartnerIdAndStatusOrderByCreatedAtAsc(partnerId, DeliveryStatus.DISPATCHED);
    }

    @Transactional
    public Delivery acknowledge(String partnerId, UUID deliveryId) {
        Delivery delivery = deliveries.findByIdAndPartnerId(deliveryId, partnerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No delivery " + deliveryId + " for partner " + partnerId));
        delivery.markAcknowledged();
        return delivery;
    }
}
