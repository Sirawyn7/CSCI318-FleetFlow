# ADR 0004 — `fleetflow-contracts`: Published Language, not Shared Kernel

**Status:** Accepted
**Owner:** Steven
**Date:** 2026-09-07

## Context

FleetFlow is graded explicitly on bounded-context isolation (`docs/AGENTS.md`:
database-per-service, no cross-service foreign keys). At the same time, five services
built in parallel need to agree on the exact shape of the 15 locked-catalogue events
(§6) — e.g. Fleet publishes `CourierLocationUpdated`, and both Dispatch and Tracking
must deserialize the same wire shape. Introducing any shared code into a project graded
on isolation is the riskiest call in the scaffold, so it needs to be deliberate and
recorded rather than emerging by accident in a later commit.

## Options considered

1. **Shared Kernel** — a library carrying shared domain types and/or behaviour (e.g. a
   common `GeoCoordinate`, shared validation). Reduces duplication but is exactly what
   DDD warns against between bounded contexts: a change for one context's needs can
   silently break another, undercutting the report's claim of genuine independence.
2. **No sharing** — every service defines and translates its own copy of every event it
   consumes. Maximally isolated, but nothing catches drift when one side changes a field
   and the other doesn't — the same failure mode that let a non-existent `Order → Fleet`
   edge survive into a work instruction before the §6.1 matrix existed.
3. **Published Language** — one thin, dependency-free module holding only the wire shape
   of each event: plain records of primitives/String/Instant/UUID/enums/collections,
   zero behaviour, zero validation. Each consumer still writes its own anti-corruption
   layer to translate the contract record into its internal model.

## The four rules that keep this a Published Language rather than a Shared Kernel

1. **Zero dependencies** — no Spring, JPA, Jackson, or Lombok. Enforced by an empty
   `<dependencies>` block in `fleetflow-contracts/pom.xml`.
2. **Records composed only of primitives, `String`, `Instant`, `UUID`, enums, collections
   of those, and other in-module records** (`EventEnvelope`, `common.GeoPoint`). No
   outside type, no behaviour, no validation.
3. **Packaged by owning context** (`order/`, `fleet/`, `dispatch/`, `tracking/`,
   `notification/`) — ownership is visible in the import, and only the owning service
   changes its own package.
4. **Importable from `infrastructure/` only** — never `domain/`, `application/`, or
   `api/`. This is the load-bearing rule: it forces a named translation class in each
   consumer's own `infrastructure/messaging/` package, which *is* the anti-corruption
   layer. Enforced per service by a duplicated `ContractBoundaryTest` rather than one
   shared test, so it survives `mvn -pl <service> test` run in isolation.

## What stays deliberately duplicated

| Duplicated | Why |
|---|---|
| `Address` | Order needs postal fields; Tracking needs only lat/lon for Haversine. |
| `GeoCoordinate` | Fleet's is source-of-truth with capture time, Dispatch's is a stale cached view, Tracking's is a series point — different lifecycles. |
| `Money`, `PackageDimensions`, `TimeWindow` | Only Order needs them. |
| `OrderStatus` vs `DeliveryStatus` | Correlated but will diverge — Tracking gains `AtRisk`, Order won't. |
| Entities, aggregates, JPA classes | Database per service — non-negotiable. |
| Kafka binding config | Tracking uses the Streams binder; the other four use plain Spring Cloud Stream. |
| Simulator's ping DTO | Duplicating it is what makes "external client" true rather than asserted. |

Five services each showing context-appropriate invariants on their own value objects is
stronger DDD evidence for the report than one shared bag three services barely use.

## Decision

Going with option 3, Published Language. The module as built (`fleetflow-contracts`,
zero deps, packaged by context, importable only from `infrastructure/`) implements it
end to end, and a per-service `ContractBoundaryTest` enforces rule 4 automatically
rather than relying on reviewer discipline to catch a stray import. Option 1 (Shared
Kernel) was rejected because it risks leaking invariants across contexts this project is
graded on keeping separate; option 2 (no sharing) was rejected because it leaves nothing
to catch the same class of drift that already caused the `Order → Fleet` false edge once.

## Consequences

- Every service consuming an event needs a named translation class in its own
  `infrastructure/messaging/` package — more boilerplate per consumer, but that
  boilerplate is the anti-corruption-layer evidence the report needs (§9.3).
- `fleetflow-contracts` becomes a coordination chokepoint: once the 15-event catalogue
  lands, a field or event-name change means every consuming service bumps its dependency
  and updates its translation class. Changes after this commit should be deliberate and
  announced, not silent.
- Rule 1 (zero dependencies, no behaviour) is non-negotiable under this decision — a
  future need for shared *behaviour* (not just shape) is a new ADR, not an addition here.
- The five `ContractBoundaryTest` copies are duplicated by design and may drift slightly
  as services evolve their own test suites; consolidating them behind ArchUnit would
  need a new dependency approval, not something to do unilaterally.
