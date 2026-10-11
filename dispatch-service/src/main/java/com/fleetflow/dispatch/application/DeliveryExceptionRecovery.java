package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.infrastructure.persistence.AssignmentRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import java.time.Instant;

/** Durable pending exceptions and publications survive restarts and do not require another Kafka event. */
@Configuration
@EnableScheduling
public class DeliveryExceptionRecovery {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeliveryExceptionRecovery.class);
    private final AssignmentRepository assignments;
    private final DeliveryExceptionService service;
    public DeliveryExceptionRecovery(AssignmentRepository assignments, DeliveryExceptionService service) {
        this.assignments = assignments; this.service = service;
    }
    @Scheduled(fixedDelayString = "${dispatch.exceptions.sweep-ms:1000}")
    public void recover() {
        for (var a : assignments.findByReassignedAtIsNotNullAndReassignmentPublishedFalse()) {
            try { service.publishPending(a.getOrderId()); }
            catch (RuntimeException e) { log.warn("Reassignment publication pending for {}", a.getOrderId(), e); }
        }
        for (var a : assignments.findByDeliveryIdIsNotNullAndDeliveryClosedFalse()) {
            try {
                service.checkOffline(a.getOrderId());
                service.escalateDue(a.getOrderId(), Instant.now());
            } catch (RuntimeException e) { log.warn("Exception recovery pending for {}", a.getOrderId(), e); }
        }
    }
}
