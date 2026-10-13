package com.fleetflow.dispatch.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ReassignmentGuardTest {
    private final Instant now = Instant.parse("2026-10-11T00:00:00Z");
    private Assignment active() {
        var a = Assignment.create(UUID.randomUUID(), UUID.randomUUID(), now);
        a.startDelivery(UUID.randomUUID()); return a;
    }
    @Test void oneAttemptThenExactTenMinuteCooldownThenEscalation() {
        var a = active();
        a.recordException("SLA_BREACH", now); a.attemptException(now);
        a.reassign(UUID.randomUUID(), "SLA_BREACH", "RULE_BASED", now);
        assertThrows(IllegalStateException.class, () -> a.attemptException(now.plusSeconds(601)));
        a.recordException("COURIER_OFFLINE", now.plusSeconds(10));
        a.escalateIfDue(now.plusSeconds(599)); assertNull(a.getEscalatedAt());
        a.escalateIfDue(now.plusSeconds(600)); assertEquals(now.plusSeconds(600), a.getEscalatedAt());
        a.escalateIfDue(now.plusSeconds(700)); assertEquals(now.plusSeconds(600), a.getEscalatedAt());
    }
    @Test void successfulRecoveryDoesNotEscalateWithoutAnotherException() {
        var a = active(); a.recordException("SLA_BREACH", now); a.attemptException(now);
        a.reassign(UUID.randomUUID(), "SLA_BREACH", "AGENT", now);
        a.escalateIfDue(now.plusSeconds(1000)); assertNull(a.getEscalatedAt());
    }
    @Test void punctuationDoesNotGiveAReasonPriorityOverNoCandidateOutcome() {
        var a = active();
        a.recordException("COURIER_OFFLINE: NO_ELIGIBLE_COURIER", now);
        a.recordException("SLA_BREACH: predicted late arrival", now.plusSeconds(1));
        assertEquals("COURIER_OFFLINE: NO_ELIGIBLE_COURIER", a.getPendingException());
    }
    @Test void colonInAnOrdinaryReasonDoesNotPreventALaterSignal() {
        var a = active();
        a.recordException("SLA_BREACH: predicted late arrival", now);
        a.recordException("COURIER_OFFLINE", now.plusSeconds(1));
        assertEquals("COURIER_OFFLINE", a.getPendingException());
    }
    @Test void noCandidateStillConsumesBudgetAndCompletionCancelsPendingEscalation() {
        var a = active(); a.recordException("NO_ELIGIBLE_COURIER", now); a.attemptException(now);
        assertThrows(IllegalStateException.class, () -> a.attemptException(now.plusSeconds(1)));
        a.closeDelivery(); a.escalateIfDue(now.plusSeconds(600)); assertNull(a.getEscalatedAt());
        a.startDelivery(a.getDeliveryId()); assertFalse(a.isDeliveryActive());
    }
}
