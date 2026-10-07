package com.fleetflow.notification.api;

import com.fleetflow.notification.application.*;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> badRequest(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    @ExceptionHandler({DeliveryDataPort.OrderNotFound.class, ExplanationService.ConversationNotFound.class})
    public ResponseEntity<ProblemDetail> notFound() { return error(HttpStatus.NOT_FOUND, "Order or conversation not found"); }
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> conflict() { return error(HttpStatus.CONFLICT, "Conversation changed; retry the question"); }
    private ResponseEntity<ProblemDetail> error(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
