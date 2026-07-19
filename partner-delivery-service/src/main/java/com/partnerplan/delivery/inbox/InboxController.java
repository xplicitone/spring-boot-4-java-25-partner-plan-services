package com.partnerplan.delivery.inbox;

import com.partnerplan.delivery.dispatch.DeliveryService;
import com.partnerplan.delivery.domain.Delivery;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pull API for non-AWS partners: poll for dispatched deliveries, then ack each one.
 *
 * Partner identity arrives in X-Partner-Id, stamped by the Istio ingress gateway after
 * it validates the partner's OAuth2 client-credentials JWT (RequestAuthentication +
 * AuthorizationPolicy). This service never trusts the header from outside the mesh.
 */
@RestController
@RequestMapping("/inbox/deliveries")
public class InboxController {

    private final DeliveryService deliveryService;

    public InboxController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping
    public List<InboxDeliveryResponse> pending(@RequestHeader("X-Partner-Id") String partnerId) {
        return deliveryService.pendingForPartner(partnerId).stream()
                .map(InboxDeliveryResponse::from)
                .toList();
    }

    @PostMapping("/{deliveryId}/ack")
    public InboxDeliveryResponse acknowledge(@RequestHeader("X-Partner-Id") String partnerId,
                                             @PathVariable UUID deliveryId) {
        return InboxDeliveryResponse.from(deliveryService.acknowledge(partnerId, deliveryId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(IllegalArgumentException e) {
        return e.getMessage();
    }

    public record InboxDeliveryResponse(
            UUID deliveryId,
            String applicationId,
            String eventType,
            String status,
            String createdAt) {

        static InboxDeliveryResponse from(Delivery d) {
            return new InboxDeliveryResponse(
                    d.getId(), d.getApplicationId(), d.getEventType(),
                    d.getStatus().name(), d.getCreatedAt().toString());
        }
    }
}
