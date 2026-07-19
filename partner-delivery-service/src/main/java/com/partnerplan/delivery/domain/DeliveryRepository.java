package com.partnerplan.delivery.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    Optional<Delivery> findByIdAndPartnerId(UUID id, String partnerId);

    List<Delivery> findByPartnerIdAndStatusOrderByCreatedAtAsc(String partnerId, DeliveryStatus status);

    boolean existsByPartnerIdAndApplicationIdAndEventType(String partnerId, String applicationId, String eventType);
}
