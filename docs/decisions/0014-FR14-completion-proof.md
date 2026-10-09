# ADR 0014 — FR14 completion proof and retry semantics

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

Lock the courier projection and then the delivery row during completion, sharing
FR13's lock order with bootstrap, arrival and recovery. Identical retries are no-ops;
changed proof conflicts. Flush, publish the internal courier snapshot, then
synchronously publish the existing DeliveryCompleted contract. Bootstrap also
publishes its snapshot before DeliveryStarted, so snapshot failure rolls back before
a lifecycle event is emitted.

Retain FR13's `CourierDeliveriesSnapshot` list and v2 topic. Build each list from
deliveries with both `arrivedAt` and `completedAt` unset. Completion removes only
that delivery; an empty list clears the final delivery. The same query is used by
startup recovery, so completed deliveries cannot be restored to monitoring. ETA,
geofence and risk joins all consume this filtered list; no per-entry completion
flag or additional topic migration is needed.

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

Prompt evidence: `docs/prompts/0013-uc7-fr14-completion-proof.md`.

## Consequences and limits

Proof is directly queryable by delivery ID and through order-scoped delivery history.
Completion does not depend on receiving a geofence event; arrival alone is not proof
of receipt. Courier ID matching is an invariant, not authentication.

Database writes and the two Kafka sends are not atomic. A partial publish or failed
database commit can leave externally visible effects; a transactional outbox would
be separate reliability work. Stream removal is eventually consistent. The existing
courier-keyed list supports concurrent deliveries. Existing rows gain nullable proof
columns. If the internal snapshot succeeds but the external send fails, monitoring
may temporarily omit a delivery until retry or recovery repairs the projection.

Tests cover domain invariants, HTTP contracts, H2 persistence, concurrent retries,
rollback on publication failure and event serialization/publication to real Kafka.

## FR13 reconciliation — 2026-10-09

*Wording drafted with AI assistance.*

The user confirmed FR13 → FR14 reconciliation. Local `main` already includes FR13
via merge `1885ba5`; FR14 incorporates that implementation and its arrival latch.
ADR 0013 and prompts 0010–0012 remain FR13's records. FR14 uses ADR 0014 and
prompt 0013. Arrival and completion remain distinct timestamps: either stops
monitoring, but only completion records proof and emits DeliveryCompleted.

Regression tests cover a failed bootstrap snapshot followed by assignment replay,
a failed completion snapshot followed by identical retries, remaining concurrent
orders, and recovery excluding completed deliveries. These checks do not establish
atomicity between Kafka and the database.
