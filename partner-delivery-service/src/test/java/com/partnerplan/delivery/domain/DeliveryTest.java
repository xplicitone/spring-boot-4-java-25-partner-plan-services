package com.partnerplan.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DeliveryTest {

    private Delivery newDelivery() {
        return new Delivery("acme", "app-1", "ApplicationApproved", ChannelType.SQS);
    }

    @Test
    void startsPendingAndDispatches() {
        Delivery delivery = newDelivery();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);

        delivery.markDispatched();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DISPATCHED);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getDispatchedAt()).isNotNull();
    }

    @Test
    void acknowledgeIsTerminal() {
        Delivery delivery = newDelivery();
        delivery.markDispatched();
        delivery.markAcknowledged();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ACKNOWLEDGED);
        assertThatThrownBy(delivery::markDispatched).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(delivery::markExpired).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedDeliveryCanBeRedispatched() {
        Delivery delivery = newDelivery();
        delivery.markFailed("queue unreachable");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getLastError()).isEqualTo("queue unreachable");

        delivery.markDispatched();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DISPATCHED);
        assertThat(delivery.getAttempts()).isEqualTo(2);
    }

    @Test
    void cannotAcknowledgeBeforeDispatch() {
        Delivery delivery = newDelivery();
        assertThatThrownBy(delivery::markAcknowledged).isInstanceOf(IllegalStateException.class);
    }
}
