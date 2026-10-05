# ADR 0013 — FR14 completion proof and retry semantics

**Status:** Accepted
**Owner:** Tenzin
**Date:** 2026-10-05

## Context

FR14 requires a completed record with route summary, timestamps and confirmation,
REST access and the existing DeliveryCompleted event. Tracking currently stores
delivery starts but no GPS history. The shared event already contains all IDs
required by its established consumers.

## Implementation proposed (AI-generated)

Keep completion proof on the Delivery aggregate: a courier-supplied route summary,
textual receipt confirmation and server completion time. Preserve the original start
time. This models the minimum requested proof without inventing GPS history, image
storage or new dependencies. The aggregate validates and freezes the evidence.

Lock the row during completion. Identical retries are no-ops; changed proof conflicts.
Flush before synchronously publishing the existing DeliveryCompleted contract.
Publish an internal snapshot with `completed=true`, and filter completed snapshots
out of all three stream-table joins. Old snapshots lacking the flag remain active.

## Options

1. **Courier-supplied text summary and confirmation on Delivery** — the implemented
   proposal. Simple REST submission and retrieval; route details are manually
   reported rather than independently derived from GPS.
2. **GPS-derived route summary plus separate receipt confirmation** — adds route
   evidence, but requires persisting and associating location history, which the
   current Tracking implementation does not do.

These alternatives were identified by the assistant; the selected approach is
recorded below.

## Decision

*Wording drafted with AI assistance.*

Use option 1: store the courier's route summary, delivery confirmation and completion
timestamp on the Delivery aggregate. This meets FR14 while keeping delivery proof
within Tracking Service and following the existing project structure. A GPS-derived
summary would require location history that the service does not currently store.

Keep the original proof unchanged after completion. Identical retries return the
saved record without publishing another `DeliveryCompleted` event, while conflicting
changes are rejected. This protects the audit record and avoids duplicate completion
events from repeated requests.

Prompt evidence: `docs/prompts/0010-uc7-fr14-completion-proof.md`.

## Consequences and limits

Proof is directly queryable by delivery ID and through order-scoped delivery history.
Completion does not depend on receiving a geofence event; arrival alone is not proof
of receipt. Courier ID matching is an invariant, not authentication.

Database writes and the two Kafka sends are not atomic. A partial publish or failed
database commit can leave externally visible effects; a transactional outbox would
be separate reliability work. Stream removal is eventually consistent. The existing
single snapshot per courier cannot represent concurrent deliveries; this slice does
not redesign FR11's keying. Existing rows gain nullable proof columns.

Tests cover domain invariants, HTTP contracts, H2 persistence, concurrent retries,
rollback on publication failure and event serialization/publication to real Kafka.
