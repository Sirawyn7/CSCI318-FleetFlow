package com.fleetflow.fleet.api;

import com.fleetflow.fleet.application.CourierRegistrationService;
import com.fleetflow.fleet.application.RegisterCourierCommand;
import com.fleetflow.fleet.domain.Courier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/couriers")
public class CourierController {

    private final CourierRegistrationService registrationService;

    public CourierController(CourierRegistrationService registrationService) {
        this.registrationService = registrationService;
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
}
