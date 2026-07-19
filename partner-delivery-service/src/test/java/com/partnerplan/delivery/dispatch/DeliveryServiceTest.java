package com.partnerplan.delivery.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.partnerplan.delivery.config.DeliveryProperties;
import com.partnerplan.delivery.domain.ChannelType;
import com.partnerplan.delivery.domain.Delivery;
import com.partnerplan.delivery.domain.DeliveryRepository;
import com.partnerplan.delivery.domain.DeliveryStatus;
import com.partnerplan.delivery.inbound.ApplicationApprovedEvent;
import com.partnerplan.delivery.partner.PartnerChannelConfig;
import com.partnerplan.delivery.partner.PartnerChannelConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock
    DeliveryRepository deliveries;

    @Mock
    PartnerChannelConfigRepository partnerConfigs;

    @Mock
    DeliveryChannel sqsChannel;

    @Mock
    DeliveryChannel inboxChannel;

    DeliveryService service;

    ApplicationApprovedEvent event =
            new ApplicationApprovedEvent("app-1", "acme", "ApplicationApproved", Instant.now());

    @BeforeEach
    void setUp() {
        when(sqsChannel.type()).thenReturn(ChannelType.SQS);
        when(inboxChannel.type()).thenReturn(ChannelType.INBOX);
        service = new DeliveryService(deliveries, partnerConfigs, List.of(sqsChannel, inboxChannel),
                new DeliveryProperties("partner-delivery-inbound", "https://partners.example.com/api"));
    }

    @Test
    void routesToPartnersConfiguredChannelWithClaimCheckUrl() {
        when(deliveries.existsByPartnerIdAndApplicationIdAndEventType("acme", "app-1", "ApplicationApproved"))
                .thenReturn(false);
        when(partnerConfigs.findById("acme")).thenReturn(Optional.of(
                new PartnerChannelConfig("acme", ChannelType.SQS, "https://sqs/queue-acme", null, true)));

        service.process(event);

        ArgumentCaptor<DeliveryMessage> message = ArgumentCaptor.forClass(DeliveryMessage.class);
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveries).save(saved.capture());
        verify(sqsChannel).dispatch(any(), message.capture(), any());
        verify(inboxChannel, never()).dispatch(any(), any(), any());

        assertThat(saved.getValue().getStatus()).isEqualTo(DeliveryStatus.DISPATCHED);
        assertThat(message.getValue().accountFetchUrl())
                .isEqualTo("https://partners.example.com/api/applications/app-1/account");
        assertThat(message.getValue().applicationId()).isEqualTo("app-1");
    }

    @Test
    void duplicateEventIsIgnored() {
        when(deliveries.existsByPartnerIdAndApplicationIdAndEventType("acme", "app-1", "ApplicationApproved"))
                .thenReturn(true);

        service.process(event);

        verify(deliveries, never()).save(any());
        verify(sqsChannel, never()).dispatch(any(), any(), any());
    }

    @Test
    void inactivePartnerConfigRejectsEvent() {
        when(deliveries.existsByPartnerIdAndApplicationIdAndEventType(any(), any(), any())).thenReturn(false);
        when(partnerConfigs.findById("acme")).thenReturn(Optional.of(
                new PartnerChannelConfig("acme", ChannelType.SQS, "https://sqs/queue-acme", null, false)));

        assertThatThrownBy(() -> service.process(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active channel config");
    }

    @Test
    void channelFailureMarksDeliveryFailedInsteadOfThrowing() {
        when(deliveries.existsByPartnerIdAndApplicationIdAndEventType(any(), any(), any())).thenReturn(false);
        when(partnerConfigs.findById("acme")).thenReturn(Optional.of(
                new PartnerChannelConfig("acme", ChannelType.SQS, "https://sqs/queue-acme", null, true)));
        doThrow(new RuntimeException("boom")).when(sqsChannel).dispatch(any(), any(), any());

        service.process(event);

        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveries).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(saved.getValue().getLastError()).isEqualTo("boom");
    }
}
