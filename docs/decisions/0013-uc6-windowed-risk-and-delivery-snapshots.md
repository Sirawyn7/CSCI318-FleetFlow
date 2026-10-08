# ADR 0013 - UC6 rolling risk windows and courier delivery snapshots

**Status:** Accepted
**Owner:** Tenzin
**Date:** 2026-09-29
**Reviewed-Date:** 2026-10-08

## Context

UC6 requires ETA, geofence milestones and windowed risk detection. Existing FR11/FR12 snapshots stored one delivery per courier, although Fleet/Dispatch allow multiple concurrent orders. Promised delivery time was projected but not passed to the stream topology. FR11 calculated arrival from processing time. Missing bootstrap prerequisites returned normally, acknowledging assignments without creating deliveries.

## Options considered

1. Extend the existing internal courier-keyed snapshot to contain all deliveries, and add a separate typed risk function with a bounded event-time window store. This preserves the existing binder/function structure and avoids database reads per ping, but duplicates joins and rewrites a courier's snapshot on assignment.
2. Replace the existing functions with a unified topology and explicit output serializers. This could share joins but expands the refactor and serialization surface.
3. Query JPA on every ping. This avoids internal snapshot schema changes but puts relational I/O on the high-frequency processing path and conflicts with the existing ADR 0010 approach.

## Implementation

Option 1 is implemented. The v2 internal topic carries a delivery list; each ping fans out to all applicable deliveries. Each delivery includes order/courier IDs, assignment start and nullable promised time. Startup republishes snapshots from the existing database; no database deletion is required.

Risk detection scans a rolling 10-minute observation window. At least 5 minutes of observations, with no gap above 2 minutes, are required for stationary/off-pace detection. Defaults: stationary radius 30 m, minimum progress 10% of straight-line expected progress, alert cooldown 10 minutes per delivery/reason. SLA prediction runs on each accepted ping, including the first. Arrival is latched in a changelogged risk store, permanently suppressing subsequent risk for that delivery even while an old snapshot is still visible. Tracking consumes its own `DeliveryArrived` event to persist `arrivedAt` and republish only unarrived deliveries; an empty snapshot clears the final delivery. Arrival ends UC6 monitoring, not FR14 proof-of-delivery completion. These are starting defaults and can be adjusted as more delivery scenarios are tested.

Kafka Streams uses `exactly_once_v2` so output and state-store updates commit together. This addresses the Kafka-only failure window noted in ADR 0011; it does not make JPA bootstrap writes and Kafka publication a distributed transaction. The earlier separate near/arrival functions and their cross-topic ordering limitation remain.

## Decision 

i Accept Option 1, as it seemed to be the best fit for us because it complements our existing ETA and Arrival Detection nicely, meaning that we won’t have to do too many modifications. It also makes perfect sense for our application to support multiple deliveries by one courier. Using a window of recent GPS observatinons data may help us detect any delays better than just using one point of data.

## Assistant-proposed rationale (not a human decision)

Previously drafted proposal for option 1: keep the existing stream functions, include all deliveries in each courier snapshot, and add a separate function for FR13 risk detection.

This builds on the working FR11 and FR12 code without needing to rewrite the whole topology. A courier can carry more than one order, so tracking needs to evaluate every assigned delivery. Keeping that information in the snapshot also avoids querying the database for every GPS update.

The observation window gives the detector enough movement history to judge progress instead of treating one GPS reading as an anomaly. The minimum observation period and gap check help avoid drawing conclusions from too little data, while the cooldown limits repeated alerts. The thresholds are a reasonable starting point for this project and remain configurable.

The ETA timestamp and bootstrap fixes are included because delayed processing or missing projections should not produce misleading estimates or silently lose an assignment. Proof-of-completion and reassignment handling remain separate work, and the transaction and event-ordering limits listed below still apply.

## Consequences

- New internal topic `fleetflow.tracking.internal.active-deliveries-by-courier-v2` and v2 application IDs isolate the changed schema from old Kafka state. Migration may re-emit milestones; consumers need their own idempotency/lifecycle handling.
- Startup recovery uses a new transaction per courier, committing and releasing its lock before the next courier. Missing projections are logged and skipped; publication failures roll back only that courier and are logged for repair/retry. Bootstrap and arrival projection use the same courier lock.
- The observation window expires old samples. Latest-ping and alert/milestone key stores still need lifecycle cleanup alongside UC7/FR14.
- Historical database rows without promised time cannot produce SLA predictions until that data is populated; stationary/off-pace detection still works.
- Tracking consumes its own arrival event for snapshot cleanup; no new cross-service consumer is added. FR14 completion, reassignment handling and a REST monitoring projection remain separate work.
- Existing historical rows have no recoverable arrival timestamp in JPA. The arrival consumer can backfill retained `DeliveryArrived` events; records older than Kafka retention need operator reconciliation. Deployments must drain the arrival backlog before treating historical snapshots as reliable.
- The arrival marker must be retained while a delivery can reappear in snapshots; lifecycle cleanup must coordinate snapshot removal and Kafka retention.
- Missing bootstrap prerequisites retry and then go to the configured DLQ; operators must replay after prerequisites are available.
- No GPS ping means no new risk evaluation. This is a GPS-window detector, not a missing-heartbeat timer.

## Evidence

See `docs/prompts/0010-uc6-completion-and-review.md`, `docs/prompts/0011-uc6-review-corrections.md`, and the Tracking tests. The prior implementation handoff reported 42 unit/regression tests and 3 real-Kafka integration tests passing across its 2026-09-29 runs. These are historical reports, not verification of the review fixes. The reviewer independently reported 157 reactor unit tests passing but could not run Docker.

Review-fix validation on 2026-10-08: the full reactor passed **163 unit/database tests** (including **48 Tracking tests**); the separate real-Kafka run passed **4 integration tests** across FR11/FR12/FR13, including arrival followed by a new assignment. No failures, errors or skips. Commands: `mvn -o test "-Dtest=*Test,!*IntegrationTest" -Dsurefire.failIfNoSpecifiedTests=false` and `mvn -o -pl tracking-service -am test "-Dtest=*IntegrationTest" -Dsurefire.failIfNoSpecifiedTests=false`. Logs: `validation-pr-review.log` and `validation-pr-review-kafka.log` (ignored local artifacts).

## Stream queries for the report (SQL-like, explanatory)

```sql
-- FR11 and FR12 share this logical input, each as a separately bound function:
SELECT ping.capturedAt, delivery.*,
       HAVERSINE(ping.location, delivery.destination) AS remaining_distance
FROM CourierLocationUpdated ping
JOIN courier_deliveries_snapshot snapshot ON ping.courierId = snapshot.courierId
CROSS JOIN UNNEST(snapshot.deliveries) delivery
WHERE ping.capturedAt >= delivery.startedAt;

-- ETA: capturedAt + remaining_distance / averageSpeed;
-- emit if no prior estimate OR ABS(newETA - lastPublishedETA) > 60 seconds.
-- Geofence: remaining_distance <= 500 / <= 50 metres, once per delivery.

-- FR13: on each fresh ping, scan bounded event-time observations for its delivery:
SELECT MIN(capturedAt), MAX(capturedAt), position_range, distance_progress
FROM delivery_observations
WHERE deliveryId = current_delivery
  AND capturedAt BETWEEN current_ping_time - INTERVAL '10' MINUTE AND current_ping_time;
-- Evaluate stationary / off pace / predicted SLA breach; suppress per-reason repeats.
```

This is a Processor API window-store aggregation, not an SQL engine or a claim that the
code uses the Kafka DSL `aggregate()` operator. Samples expire from the window store;
per-delivery marker keys still need lifecycle cleanup.


## Operational details

Vehicle speeds are configured under `tracking.eta.speed-kmh` (bicycle 15, motorcycle 35, car 30, van 28, truck 22 km/h). A custom vehicle property is resolved before the hardcoded fallback. Configuration and risk defaults are in `tracking-service/src/main/resources/application.yml`.

The API layer has no monitoring endpoint or persisted ETA/risk history yet. Stream-table joins do not recover pings received before snapshot materialization. Samples at or before the latest processed timestamp are ignored; future-dated GPS validation remains Fleet’s ingestion responsibility. Separate near/arrival topics do not guarantee observation order.

The earlier broker run encountered Windows RocksDB path-length limits: integration tests use short state paths and named KTable stores. Tests use isolated databases and never clear the user’s H2 database.
