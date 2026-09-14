package com.fleetflow.fleet.api;

import com.fleetflow.fleet.application.ChangeCourierAvailabilityCommand;
import com.fleetflow.fleet.application.CourierRegistrationService;
import com.fleetflow.fleet.application.CourierAvailabilityService;
import com.fleetflow.fleet.application.RegisterCourierCommand;
import com.fleetflow.fleet.domain.Courier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@RestController
@RequestMapping("/api/couriers")
public class CourierController {

    private final CourierRegistrationService registrationService;
    private final CourierAvailabilityService availabilityService;

    public CourierController(CourierRegistrationService registrationService,  CourierAvailabilityService availabilityService) {
        this.registrationService = registrationService;
        this.availabilityService = availabilityService;
    }

    @PostMapping
    public ResponseEntity<RegisterCourierResponse> register(@RequestBody RegisterCourierRequest request) {
        Courier courier = registrationService.registerCourier(
                new RegisterCourierCommand(request.vehicleType(), request.capacity(), request.operatingZone()));

        RegisterCourierResponse response = new RegisterCourierResponse(
                courier.getId(),
                courier.getVehicleType().name(),
                courier.getCapacity(),
                courier.getOperatingZone(),
                courier.getStatus().name());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{courierId}/availability")
    public ResponseEntity<ChangeAvailabilityResponse> changeAvailability(
            @PathVariable UUID courierId,
            @RequestBody ChangeAvailabilityRequest request) {
        Courier courier = availabilityService.changeAvailability(
                new ChangeCourierAvailabilityCommand(courierId, request.available()));

        return ResponseEntity.ok(new ChangeAvailabilityResponse(courier.getId(), courier.getStatus().name()));
    }
}
