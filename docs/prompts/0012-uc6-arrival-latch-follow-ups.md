# Prompt 0012 — UC6 arrival latch follow-ups

**Date:** 2026-10-08
**Used for:** ADR 0013 review follow-up implementation notes and regression coverage
**Why this one's worth citing:** Multi-turn refinement: the reviewer challenged the single-ping latch and required an explicit consistency choice for ETA/geofence.

## Prompt (verbatim excerpt)

> At minimum, don't latch on a single ping — require the same kind of sustained signal the rest of risk detection already demands (e.g. N consecutive within-radius pings, or confirm against the real `DeliveryArrived` event from `DeliveryArrivalService` rather than re-deriving arrival from a single distance check in a second place).

## Response (representative verbatim excerpt)

> The risk latch currently trips on one ping. I’ll require three consecutive, strictly newer within-radius pings, resetting the count on an outside ping or an excessive gap. I’ll also document the permitted ETA/geofence propagation lag, mark the evaluator check as defensive, and explicitly name the unfiltered repository method for cleanup use.

Human acceptance and rationale are not supplied by the assistant.
