package com.fleetflow.tracking.application;

import com.fleetflow.tracking.domain.Delivery;
import com.fleetflow.tracking.infrastructure.messaging.DeliveryCompletedEventPublisher;
import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshotPublisher;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class DeliveryCompletionService {
    private final DeliveryJpaRepository repository;
    private final DeliveryCompletedEventPublisher publisher;
    private final ActiveDeliverySnapshotPublisher snapshots;

    public DeliveryCompletionService(DeliveryJpaRepository repository,
            DeliveryCompletedEventPublisher publisher, ActiveDeliverySnapshotPublisher snapshots) {
        this.repository = repository;
        this.publisher = publisher;
        this.snapshots = snapshots;
    }

    @Transactional
    public Delivery complete(UUID id, UUID courierId, String routeSummary, String confirmation) {
        if (courierId == null) throw new IllegalArgumentException("courierId is required");
        Delivery delivery = repository.findForCompletion(id)
                .orElseThrow(() -> new NoSuchElementException("Delivery not found"));
        if (delivery.complete(courierId, routeSummary, confirmation, Instant.now())) {
            repository.saveAndFlush(delivery);
            publisher.publish(delivery);
            snapshots.publish(delivery);
        }
        return delivery;
    }

    @Transactional(readOnly = true)
    public Delivery get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Delivery not found"));
    }

    @Transactional(readOnly = true)
    public List<Delivery> history(UUID orderId) {
        return repository.findByOrderIdOrderByStartedAtAsc(orderId);
    }
}
