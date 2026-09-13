package com.fleetflow.fleet.application;

import com.fleetflow.fleet.domain.Courier;
import com.fleetflow.fleet.domain.GeoCoordinate;
import com.fleetflow.fleet.infrastructure.messaging.CourierLocationUpdatedEventPublisher;
import com.fleetflow.fleet.infrastructure.persistence.CourierJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Use-case orchestration and the transactional boundary, same shape as
 * CourierAvailabilityService. capturedAt is set here (server receipt time), not taken
 * from the request -- the GPS Simulator is an untrusted external client, so trusting a
 * caller-supplied timestamp would let a stale or clock-skewed reading overwrite a newer
 * one. NoSuchElementException here is mapped to a 404 by ApiExceptionHandler.
 */

@Service
public class CourierLocationService {
    private final CourierJpaRepository courierRepository;
    private final CourierLocationUpdatedEventPublisher eventPublisher;

    public CourierLocationService(CourierJpaRepository courierRepository, CourierLocationUpdatedEventPublisher eventPublisher) {
        this.courierRepository = courierRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Courier ingestLocation(IngestCourierLocationCommand command) {
        Courier courier = courierRepository.findById(command.courierId())
                .orElseThrow(() -> new NoSuchElementException("Courier not found: " + command.courierId()));

        GeoCoordinate location = new GeoCoordinate(command.latitude(), command.longitude());
        courier.updateLocation(location, Instant.now());
        courierRepository.save(courier);
        eventPublisher.publish(courier);
        return courier;
    }
}
