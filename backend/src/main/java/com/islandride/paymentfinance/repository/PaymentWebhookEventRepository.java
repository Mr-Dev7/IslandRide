package com.islandride.paymentfinance.repository;

import com.islandride.paymentfinance.domain.PaymentWebhookEvent;
import com.islandride.paymentfinance.domain.WebhookProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {

    Optional<PaymentWebhookEvent> findByGatewayProviderAndGatewayEventId(String gatewayProvider, String gatewayEventId);

    Optional<PaymentWebhookEvent> findByPublicId(String publicId);

    List<PaymentWebhookEvent> findByProcessingStatus(WebhookProcessingStatus status);
}

