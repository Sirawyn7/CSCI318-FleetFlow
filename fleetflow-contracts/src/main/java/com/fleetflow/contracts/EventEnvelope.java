package com.fleetflow.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata every published event carries as the first component of its record.
 * Lives in the payload, not Kafka headers, so a consumer's idempotency check is a
 * two-line {@code envelope.eventId()} lookup against its own {@code processed_events}
 * ledger rather than header-extraction plumbing.
 */
public record EventEnvelope(UUID eventId, String eventType, Instant occurredAt, String correlationId) {
}
