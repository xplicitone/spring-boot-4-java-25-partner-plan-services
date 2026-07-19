package com.partnerplan.delivery.inbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.partnerplan.delivery.dispatch.DeliveryService;
import com.partnerplan.delivery.domain.ChannelType;
import com.partnerplan.delivery.domain.Delivery;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InboxControllerTest {

    @Mock
    DeliveryService deliveryService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InboxController(deliveryService)).build();
    }

    private Delivery dispatchedDelivery() {
        Delivery delivery = new Delivery("acme", "app-1", "ApplicationApproved", ChannelType.INBOX);
        delivery.markDispatched();
        return delivery;
    }

    @Test
    void listsPendingDeliveriesForCallingPartner() throws Exception {
        when(deliveryService.pendingForPartner("acme")).thenReturn(List.of(dispatchedDelivery()));

        mockMvc.perform(get("/inbox/deliveries").header("X-Partner-Id", "acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicationId").value("app-1"))
                .andExpect(jsonPath("$[0].status").value("DISPATCHED"));
    }

    @Test
    void acknowledgesDelivery() throws Exception {
        Delivery delivery = dispatchedDelivery();
        delivery.markAcknowledged();
        when(deliveryService.acknowledge(eq("acme"), any(UUID.class))).thenReturn(delivery);

        mockMvc.perform(post("/inbox/deliveries/{id}/ack", UUID.randomUUID())
                        .header("X-Partner-Id", "acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }

    @Test
    void unknownDeliveryReturns404() throws Exception {
        when(deliveryService.acknowledge(eq("acme"), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(post("/inbox/deliveries/{id}/ack", UUID.randomUUID())
                        .header("X-Partner-Id", "acme"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingPartnerHeaderIsRejected() throws Exception {
        mockMvc.perform(get("/inbox/deliveries"))
                .andExpect(status().isBadRequest());
    }
}
