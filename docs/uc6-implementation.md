# UC6 implementation and human-writing handoff

Branch: `UC6-FR11&12&13`. Session: 2026-09-29. Existing FR11 and FR12 were reviewed
and extended; FR13 was added. No dependencies or public event types were added.

## What the assistant changed

| Requirement | Behavior and evidence |
|---|---|
| FR11 | ETA uses GPS capture time + Haversine distance / configured vehicle speed. First estimate publishes; later changes must exceed 60 seconds. Stale/duplicate timestamps are ignored. See `EtaRecalculationTopology`, `EtaMaterialChangeProcessor`, `EtaCalculationService`, and their tests. |
| FR12 | Existing 500 m near / 50 m arrival milestones retained, each once per delivery; now fans out across all courier deliveries. `GeofenceDetectionTopology`, `NearDestinationProcessor`, `ArrivedProcessor`, domain/processor and real-broker tests. |
| FR13 | `RiskDetectionTopology` and `RiskDetectionProcessor` aggregate a rolling event-time window of delivery-scoped GPS observations. `RiskEvaluationService` classifies stationary, poor progress and predicted SLA breach, then publishes the existing `DeliveryAtRisk` contract. |
| Shared correctness | Snapshots hold multiple deliveries and promised time. Courier-level database locking serializes snapshot changes. Missing bootstrap inputs throw for retry/DLQ instead of acknowledging the assignment. Startup recovers internal snapshots. Kafka-only state/output uses exactly-once processing. |

Location data stays owned by Fleet. Tracking owns Delivery; snapshots/window observations
are derived read models. The domain evaluator has no Spring or Kafka dependency.
Tracking's API layer remains empty: current UC6 requirements are streaming event outputs;
a queryable monitoring projection is not claimed here.

## Risk defaults and semantics

| Configuration | Default | Meaning |
|---|---|---|
| `tracking.risk.window` | `PT10M` | Rolling observation range; RocksDB retains an additional 5 minutes before expiration. |
| `minimum-observation` | `PT5M` | Minimum span before stationary/off-pace classification. |
| `maximum-ping-gap` | `PT2M` | Larger gaps prevent claiming continuous stationary/slow behavior. |
| `stationary-radius-meters` | 30 | All observed points must remain within this radius of the first point. |
| `minimum-progress-ratio` | 0.1 | Progress toward destination below 10% of speed x elapsed time is off pace, unless classified stationary. |
| `alert-cooldown` | `PT10M` | Suppress repeated delivery/reason alerts; distinct reasons may each emit. |

SLA breach means predicted ETA is strictly after promised time; equality is not a breach.
It does not need a full window. Missing promised time disables only that rule. A ping
inside the arrival radius suppresses risk at that observation. No ping means no new
calculation: loss-of-signal detection is not implemented. Samples at or before the
latest processed timestamp are ignored. Pings captured before assignment are excluded.

Defaults are implementation proposals for human review, not invented evidence of team
approval. Vehicle speeds are configured under `tracking.eta.speed-kmh`: bicycle 15,
motorcycle 35, car 30, van 28, truck 22 km/h.

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

## Validation

Validation completed on 2026-09-29 with Java 21, Maven and Docker Desktop:

- Full Tracking suite: **40 tests passed**, zero failures/errors/skips, including
  **3 real-Kafka Testcontainers integration tests** (FR11, FR12 and FR13).
- After adding five further bootstrap/recovery/configuration regression cases:
  **42 unit/regression tests passed**, zero failures/errors/skips. Production code
  was unchanged after the successful broker run.
- **45 distinct tests passed across these runs.** `git diff --check` also passed.
- Commands: `mvn -o -pl tracking-service -am test`, then
  `mvn -o -pl tracking-service -am test "-Dtest=*Test,!*IntegrationTest" -Dsurefire.failIfNoSpecifiedTests=false`.
- Local output: `validation-uc6-broker.log` and `validation-uc6.log` (ignored log files).

The first broker run exposed Windows path-length limits in RocksDB. Named short
KTable stores and short isolated test state paths resolved it; the rerun passed.

Tests include domain boundaries,
GPS jitter/sparse observations, event-time ETA, duplicate/out-of-order suppression,
bootstrap retry, both geofence events, and a real-broker FR13 scenario with two orders
assigned to one courier. Tests use isolated databases, broker containers and short
state-store paths; they do not clear the user's H2 database.

## Integration limits to state accurately

- No monitoring REST endpoint or persisted ETA/risk history yet; FR14 proof/completion
  and later explainer queries need their own projection/lifecycle work.
- Completion/reassignment cleanup is not wired through inferred consumer edges. Until
  those slices are integrated, snapshots retain deliveries and marker keys remain.
- Near and arrived are separate Kafka topics; observation order is not guaranteed.
- Stream-table joins cannot recover a ping that arrived before the snapshot was
  materialized. Continued GPS pings provide the next evaluation.
- Kafka Streams transactions cover stream offsets/state/output, not JPA bootstrap plus
  Kafka. A database commit failure after a successful send remains a consistency gap.
- v2 internal schema/application IDs require migration awareness; startup republishes
  existing snapshots, but milestones may re-emit. Old deliveries may have null deadlines.
- Expired samples and stale pings are not incorporated retroactively. Future-dated GPS
  can advance stream time; clock-skew validation remains Fleet's ingestion responsibility.

## Human writing required - exact places

1. **ADR 0013, `Decision - HUMAN WRITING REQUIRED`:** fill Owner, choose accept/reject/
   modify, explain your judgment about window thresholds, multiple-delivery snapshots,
   and the tradeoffs. Update Status only after review. Context, options and technical
   consequences have been drafted for you.
2. **`docs/ai-collaboration-log.md`:** add a short entry pointing to ADR 0013 and prompt
   0010. You must write the Decision and Rationale yourself. The assistant has not edited
   the log. Use the draft supplied in chat as raw material, not as a claimed acceptance.
3. **Report Section 5:** explain what you actually reviewed, tested, accepted or changed.
   Cite the verbatim prompt separately from its expanded reusable version. Describe the
   timestamp/snapshot corrections and your own judgment; do not present the edited
   prompt as an original human transcript.
4. **Member contribution/accountability log:** record the real reviewer/implementer,
   their actual contribution and date. No names or work allocations were invented.
5. **Report architecture/DDD/streaming sections:** use the classes and explanatory queries
   above as evidence; add your own justification for the patterns and acknowledge the
   listed limits. The human author should verify every claim before submission.

The repository's `docs/AGENTS.md` says humans write decision/rationale; the ADR template
also reserves its Decision section for the accountable team member. These placeholders
are deliberate authorship boundaries, not missing code implementation.

Technical reference: Spring documents custom StoreBuilder registration and timestamp
extractor configuration in its [Kafka Streams binder reference](https://docs.spring.io/spring-cloud-stream-binder-kafka/docs/current/reference/html/kafka-streams.html).
