package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.domain.GeoCoordinate;
import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityView;

/** The same eligibility policy is used for ranking and locked commit-time validation. */
final class CourierEligibility {
    private CourierEligibility() { }

    static DispatchContext.CourierSnapshot snapshot(CourierAvailabilityView courier, GeoCoordinate pickup,
                                                   int pending, OperatingZones zones) {
        GeoCoordinate location = courier.getLatitude() == null || courier.getLongitude() == null
                ? null : new GeoCoordinate(courier.getLatitude(), courier.getLongitude());
        int remaining = Math.max(0, courier.getCapacity() - courier.getActiveOrderCount() - pending);
        boolean eligible = courier.isAvailable() && location != null && remaining > 0
                && zones.serves(courier.getOperatingZone(), pickup);
        return new DispatchContext.CourierSnapshot(courier.getCourierId(), courier.getVehicleType(),
                courier.getOperatingZone(), location, courier.getCapacity(), courier.getActiveOrderCount(),
                pending, remaining, eligible);
    }
}
