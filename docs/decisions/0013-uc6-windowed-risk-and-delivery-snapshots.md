# ADR 0013 - UC6 rolling risk windows and courier delivery snapshots

**Status:** Accepted
**Owner:** Tenzin
**Date:** 2026-09-29

## Context

UC6 requires ETA, geofence milestones and windowed risk detection. Existing FR11/FR12 snapshots stored one delivery per courier, although Fleet/Dispatch allow multiple concurrent orders. Promised delivery time was projected but not passed to the stream topology. FR11 calculated arrival from processing time. Missing bootstrap prerequisites returned normally, acknowledging assignments without creating deliveries.

## Options considered

1. Extend the existing internal courier-keyed snapshot to contain all deliveries, and add a separate typed risk function with a bounded event-time window store. This preserves the existing binder/function structure and avoids database reads per ping, but duplicates joins and rewrites a courier's snapshot on assignment.
2. Replace the existing functions with a unified topology and explicit output serializers. This could share joins but expands the refactor and serialization surface.
3. Query JPA on every ping. This avoids internal snapshot schema changes but puts relational I/O on the high-frequency processing path and conflicts with the existing ADR 0010 approach.

## Implementation

Option 1 is implemented. The v2 internal topic carries a delivery list; each ping fans out to all applicable deliveries. Each delivery includes order/courier IDs, assignment start and nullable promised time. Startup republishes snapshots from the existing database; no database deletion is required.

Risk detection scans a rolling 10-minute observation window. At least 5 minutes of observations, with no gap above 2 minutes, are required for stationary/off-pace detection. Defaults: stationary radius 30 m, minimum progress 10% of straight-line expected progress, alert cooldown 10 minutes per delivery/reason. SLA prediction runs on each accepted ping, including the first. Arrival suppresses risk for the current ping. These are starting defaults and can be adjusted as more delivery scenarios are tested.

Kafka Streams uses `exactly_once_v2` so output and state-store updates commit together. This addresses the Kafka-only failure window noted in ADR 0011; it does not make JPA bootstrap writes and Kafka publication a distributed transaction. The earlier separate near/arrival functions and their cross-topic ordering limitation remain.

## Decision

Accepted option 1: keep the existing stream functions, include all deliveries in each courier snapshot, and add a separate function for FR13 risk detection.

This builds on the working FR11 and FR12 code without needing to rewrite the whole topology. A courier can carry more than one order, so tracking needs to evaluate every assigned delivery. Keeping that information in the snapshot also avoids querying the database for every GPS update.

The observation window gives the detector enough movement history to judge progress instead of treating one GPS reading as an anomaly. The minimum observation period and gap check help avoid drawing conclusions from too little data, while the cooldown limits repeated alerts. The thresholds are a reasonable starting point for this project and remain configurable.

The ETA timestamp and bootstrap fixes are included because delayed processing or missing projections should not produce misleading estimates or silently lose an assignment. Completion and reassignment cleanup remain separate work, and the transaction and event-ordering limits listed below still apply.

## Consequences

- New internal topic `fleetflow.tracking.internal.active-deliveries-by-courier-v2` and v2 application IDs isolate the changed schema from old Kafka state. Migration may re-emit milestones; consumers need their own idempotency/lifecycle handling.
- The observation window expires old samples. Latest-ping and alert/milestone key stores still need lifecycle cleanup alongside UC7/FR14.
- Historical database rows without promised time cannot produce SLA predictions until that data is populated; stationary/off-pace detection still works.
- Tracking completion/reassignment cleanup and a REST monitoring projection remain separate integration work; this branch adds no inferred cross-service consumers.
- Missing bootstrap prerequisites retry and then go to the configured DLQ; operators must replay after prerequisites are available.
- No GPS ping means no new risk evaluation. This is a GPS-window detector, not a missing-heartbeat timer.

## Evidence

See `docs/prompts/0010-uc6-completion-and-review.md`, `docs/uc6-implementation.md`, and the Tracking tests. Acceptance was requested by the user. The assistant drafted the decision wording and rationale from the implementation tradeoffs; this is not a verbatim human reflection.
