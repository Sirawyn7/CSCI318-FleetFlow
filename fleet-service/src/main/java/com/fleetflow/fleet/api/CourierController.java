package com.fleetflow.fleet.api;

import com.fleetflow.fleet.application.ChangeCourierAvailabilityCommand;
import com.fleetflow.fleet.application.CourierAvailabilityService;
import com.fleetflow.fleet.application.CourierLocationService;
import com.fleetflow.fleet.application.CourierManagementService;
import com.fleetflow.fleet.application.CourierRegistrationService;
import com.fleetflow.fleet.application.IngestCourierLocationCommand;
import com.fleetflow.fleet.application.RegisterCourierCommand;
import com.fleetflow.fleet.domain.Courier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/couriers")
public class CourierController {

    private final CourierRegistrationService registrationService;
    private final CourierAvailabilityService availabilityService;
    private final CourierLocationService locationService;
    private final CourierManagementService managementService;

    public CourierController(CourierRegistrationService registrationService,
                              CourierAvailabilityService availabilityService,
                              CourierLocationService locationService,
                              CourierManagementService managementService) {
        this.registrationService = registrationService;
        this.availabilityService = availabilityService;
        this.locationService = locationService;
        this.managementService = managementService;
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

    /**
     * FR10: GPS Simulator posts pings here. POST rather than PUT/PATCH -- each call is a
     * new location reading (a fact that occurred at a point in time), not a partial edit
     * of the courier resource, even though Fleet only persists the latest reading
     * (locked decision, see CourierLocationUpdated's Javadoc).
     */
    @PostMapping("/{courierId}/location")
    public ResponseEntity<IngestCourierLocationResponse> ingestLocation(
            @PathVariable UUID courierId,
            @RequestBody IngestCourierLocationRequest request) {
        Courier courier = locationService.ingestLocation(
                new IngestCourierLocationCommand(courierId, request.latitude(), request.longitude()));

        return ResponseEntity.ok(new IngestCourierLocationResponse(
                courier.getId(),
                courier.getCurrentLocation().latitude(),
                courier.getCurrentLocation().longitude(),
                courier.getLocationUpdatedAt()));
    }

    @GetMapping
    public List<CourierResponse> list() {
        return managementService.list().stream().map(CourierResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CourierResponse get(@PathVariable UUID id) {
        return CourierResponse.from(managementService.get(id));
    }

    @PutMapping("/{id}")
    public CourierResponse update(@PathVariable UUID id,
                                   @RequestBody RegisterCourierRequest request) {
        return CourierResponse.from(managementService.update(id,
                new RegisterCourierCommand(request.vehicleType(), request.capacity(), request.operatingZone())));
    }

    @PostMapping("/{id}/deactivate")
    public CourierResponse deactivate(@PathVariable UUID id) {
        return CourierResponse.from(managementService.deactivate(id));
    }
}