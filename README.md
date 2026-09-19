# FleetFlow

## UC3 — Assign courier to order (FR7, FR8)

Dispatch now tries the LangChain4j/Gemini agent first. Its read-only tools inspect
fleet load, positions and the open-order backlog. The whole agent loop has a
5-second deadline. Provider errors, invalid recommendations and timeouts fall back
to deterministic rules: matching pickup zone, available capacity, nearest courier,
then remaining capacity and courier ID as tie-breakers.

`assignment_decisions` records the path and justification or fallback/failure reason.
Fleet publishes `CourierLoadChanged`; Dispatch reconciles pending reservations so
consecutive assignments cannot consume the same last slot while load events lag.
The AI call runs outside database transactions. Commit-time checks lock only the
chosen courier and target order; stale recommendations fall back to refreshed rules.
Historical load acknowledgements without a local assignment are logged and applied,
and adjacent zone rectangles use minimum-inclusive, maximum-exclusive boundaries.

Set `GEMINI_API_KEY` in the Dispatch process environment for live AI dispatch.
`GEMINI_MODEL` selects the provider model. **The retained project default,
`gemini-2.0-flash`, has been retired by Google; a supported replacement must be
agreed before a live agent demo.** Without usable credentials/model, FR7 still runs.
No test requires live Gemini access or spends model quota.

The configured `ZONE_A` and `ZONE_B` rectangles are demo areas, not official
boundaries. Configure `dispatch.zones` for the fleet's actual service areas.
Unknown zones and pickups outside a courier's area are ineligible on both paths.

See [UC3 implementation and demo guide](docs/uc3-implementation.md) for setup,
audit queries, test commands, event ordering and remaining integration limits.

## UC2 — Fleet profiles and load (FR4, FR6)

Fleet runs on port 8082. FR5 shift controls remain a separate team slice.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/couriers` | Register and publish `CourierRegistered` |
| GET | `/api/couriers` | List profiles, including deactivated couriers |
| GET | `/api/couriers/{id}` | View profile and active-order count |
| PUT | `/api/couriers/{id}` | Replace vehicle type, capacity and operating zone |
| POST | `/api/couriers/{id}/deactivate` | Deactivate a courier with no active orders |

Registration and update accept, for example:

```json
{"vehicleType":"VAN","capacity":3,"operatingZone":"ZONE_A"}
```

GET, PUT and deactivation responses include `activeOrderCount` and `deactivated`.
Invalid profile values return 400; missing couriers return 404; capacity and
deactivation conflicts return 409. Deactivation is idempotent and separate from
FR5's on/off-shift status. Capacity cannot be reduced below current load.

Fleet consumes `fleetflow.dispatch.order-assigned` and
`fleetflow.tracking.delivery-completed` in consumer group `fleet-service`.
The load counter, order ledger and processed-event ledger commit together.
Duplicate events do not double-count; completed orders stay completed even if their
assignment arrives later. Rejected messages retry then go to the binder's dead-letter
topics (`error.<source-topic>.fleet-service`) for investigation/replay.

**Integration boundaries:** FR6 protects Fleet's local capacity invariant; consuming
an already-published assignment cannot undo Dispatch's decision. Dispatch now
checks reported load plus pending reservations before assigning (UC3). Profile updates and deactivation currently
persist in Fleet only: the existing event catalogue has no profile-update or
deactivation event, so these changes are not propagated to Dispatch's view in this
slice. FR5 integration must respect the deactivation flag. Reassignment and
cancellation load adjustments remain outside this slice, per ADR 0002.

Run all Fleet tests (requires Docker for the real Kafka test):

```text
mvn -pl fleet-service -am test
```

Run domain, API and H2 persistence tests without Docker:

```text
mvn -pl fleet-service -am test -Dtest=CourierTest,CourierManagementTest,CourierRegistrationServiceTest,CourierControllerTest,CourierPersistenceTest,ContractBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false
```

An AI-powered, cloud-native delivery and logistics tracking platform built as a
Spring Boot microservices system for CSCI318. See [`docs/AGENTS.md`](docs/AGENTS.md) for
the full architecture and hard constraints, and
[`docs/requirements-outline.md`](docs/requirements-outline.md) for the requirements.

## Module map

| Module | Type | Port | Responsibility |
|---|---|---|---|
| `order-service` | Service | 8081 | Order lifecycle, cancellation (FR1-FR3) |
| `fleet-service` | Service | 8082 | Courier profiles, availability, capacity, location (FR4-FR6, FR10) |
| `dispatch-service` | Service | 8083 | Courier assignment (agentic + rule-based), reassignment (FR7-FR9) |
| `tracking-service` | Service | 8084 | Location stream processing, ETA, geofencing, risk detection (FR11-FR14) |
| `notification-service` | Service | 8085 | Templated alerts, conversational status explainer (FR15-FR16) |
| `gps-simulator` | External client | — | Posts simulated location pings to Fleet's REST endpoint (FR10) |
| `fleetflow-contracts` | Contract artifact | — | Shared event records for the 16-event §6 catalogue |
| Kafka | Broker | 9092 | Event backbone. Order, Fleet and Dispatch have explicit event bindings |

## Structural exceptions

Every service uses the same four-layer package structure
(`api/ → application/ → domain/ → infrastructure/`, see `docs/AGENTS.md`). Two modules
deliberately do not follow it:

- **`gps-simulator`** — it is an external REST client, not a bounded-context service: it
  owns no aggregate, exposes no REST API of its own, and has no direct Kafka access
  (per the locked design decision in `docs/AGENTS.md`). A flat `com.fleetflow.simulator`
  package is the honest structure for that role.
- **`fleetflow-contracts`** — it is a contract artifact shared by the five services, not
  a service itself, so it has no layers to have. See `docs/decisions/0004-fleetflow-contracts-published-language.md`
  for why this is a Published Language and not a Shared Kernel.

## Prerequisites

- **JDK 21** (Temurin recommended)
- **Apache Maven 3.9+**

Verify your toolchain:

```
java -version
mvn -version
```

## Build and run

Build the whole reactor:

```
mvn clean install
```

Run a single service (each service is independently runnable and owns its own H2
database file under `<service>/data/`):

```
mvn -pl order-service spring-boot:run
```

Kafka is a mandated dependency of the project. Fleet and Dispatch now have a real
binding for `CourierRegistered` (see `docs/decisions/0004-fleetflow-contracts-published-language.md`
for the pattern); Order also has explicit `OrderPlaced` and `OrderCancelled` output bindings;
Tracking and Notification have no bindings configured, and there's no committed Docker Compose setup
yet — that's a later commit.

For local development or a manual demo, run a single-node broker yourself, e.g.:

```
docker run -p 9092:9092 apache/kafka:latest
```

(Confirm the current tag on Docker Hub — pin an exact version rather than `latest` for
anything beyond a one-off local demo.)

## Order slice validation

`POST /orders` creates an order (201 and Location header), `GET /orders/{id}` returns
its response DTO and status history, and `POST /orders/{id}/cancel` cancels a placed
order. Missing orders return 404, invalid input returns 400, and cancellation outside
PLACED returns 409. A repeated cancellation does not publish another event.

Run Order and its contract build with `mvn -pl order-service -am verify`. Order tests cover domain validation,
HTTP contracts, JPA round trips, and rollback of flushed writes when publication fails.

For the real Kafka test, start Docker Desktop (using its WSL 2 backend on Windows),
then run:

```text
docker info
wsl --list --verbose
mvn -pl order-service -am test -Dkafka.integration=true
```

Testcontainers starts an isolated `confluentinc/cp-kafka:7.8.0` broker on a random port and
removes it after the test. It verifies both Order event payloads on their configured
topics. The Kafka test is opt-in and requires Docker; the normal suite skips it.
No separate Kafka installation inside a WSL distribution is required.

Order maps its aggregate directly with JPA, following Fleet. Its history is stored in
`order_status_history`; existing orders retain their original columns. Existing database
rows have no historical transitions to backfill automatically: the implementation does
not invent timestamps for old transitions. New orders store their items in `order_items`. Each item has a description (1–255
characters) and positive integer quantity. Send `"items":[{"description":"Book","quantity":2}]`
in the creation body. If omitted, package details become one item with quantity 1;
`packageDetails` remains the overall package summary. Existing database orders are
preserved and may have an empty item list because their quantities were never recorded.
The inferred Order lifecycle event consumers still require team agreement. Do not delete the existing H2 file to test these changes; tests use isolated
in-memory databases.

Text fields are limited to 255 characters to match the schema. Saves flush SQL before
publication, so database constraint violations cannot emit an event first. Cancellation
locks the order row until transaction completion; a concurrent cancellation waits and
then sees the committed status. Regression tests cover both behaviours.

Publication failures roll back database writes. Synchronous Kafka sends surface broker
errors, but JPA and Kafka are not a distributed transaction: a database commit failure
after a successful send can still leave an emitted event. Exactly-once creation and
retry idempotency are not guaranteed by this transaction boundary.
