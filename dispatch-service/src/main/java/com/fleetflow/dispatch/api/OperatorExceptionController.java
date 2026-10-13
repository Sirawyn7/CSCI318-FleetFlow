package com.fleetflow.dispatch.api;

import com.fleetflow.dispatch.application.DeliveryExceptionService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/dispatch/exceptions")
public class OperatorExceptionController {
    private final DeliveryExceptionService service;
    public OperatorExceptionController(DeliveryExceptionService service) { this.service = service; }
    @GetMapping public List<DeliveryExceptionService.OperatorException> list() { return service.operatorQueue(); }
}
