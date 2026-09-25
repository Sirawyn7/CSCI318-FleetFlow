# ADR 0011 — FR12's geofence thresholds and the two-function split

**Status:** Accepted
**Owner:** Landon
**Date:** 2026-09-23

## Context

FR12 (`requirements-outline.md` §4, UC6) evaluates each `CourierLocationUpdated` ping
against the destination geofence radius, publishing `DeliveryNearDestination` then
`DeliveryArrived`. Two decisions here weren't settled by any existing document.

**First:** what counts as "near" and "arrived"? The outline's own open items list
"Geofence radius and SLA thresholds" as something to "pick now, tune during testing."

**Second:** one Kafka Streams function has exactly one output type/topic
(`spring.cloud.function` binds each function's return type to its own `-out-0`).
FR12 needs to emit two different event types (`DeliveryNearDestination`,
`DeliveryArrived`) to two different topics from logically the same evaluation.

## Options considered

**For the multi-event-type problem:**
1. **Two separate topology functions**, one per event type, each doing its own
   stream-table join over the same two input topics.
2. **A single join with a multi-output array return**
   (`BiFunction<..., KStream<String, ?>[]>`), splitting the joined stream by event
   type via Kafka Streams' `.split()`, bound to `-out-0`/`-out-1`. More "correct" in
   the sense of computing the join once, but Spring Cloud Stream's Kafka Streams
   binder needs concrete generic type information per output to configure
   serialization -- a wildcard/`Object`-typed branched stream is exactly the kind of
   thing that can silently misconfigure without a live broker to verify against, and
   this project has already hit two real (if different) YAML/binder configuration
   bugs getting FR11's simpler single-output topology working correctly.
3. **A single join dropping to the raw `Topology`/`StreamsBuilder` API**, calling
   `.to(topic, ...)` directly inside the function body for a manual side output. The
   most "idiomatic Kafka Streams" answer for this scenario, but a real departure from
   how every other topology in this codebase is built (function-return-based), and
   requires manually wiring a Serde instead of relying on the binder's automatic JSON
   conversion.

## Decision

**Thresholds:** 500m for "near," 50m for "arrived" (`GeofenceEvaluationService`). 500m
gives a customer roughly a minute or two of notice at typical last-mile speeds (per
FR11's own average-speed defaults); 50m is tight enough to tolerate ordinary GPS
jitter without firing early, while staying comfortably inside the near threshold --
`GeofenceEvaluationServiceTest` asserts `ARRIVAL_THRESHOLD_METERS <=
NEAR_THRESHOLD_METERS` directly, so a delivery can never "arrive" without its
location having also been inside the near radius.

Note this is a spatial nesting guarantee only, not an ordering guarantee: the two
topology functions are independent and write to different topics, so consumers
should not assume `DeliveryNearDestination` is always observed before
`DeliveryArrived` for the same delivery.

**Option 1** (two separate functions) was chosen over options 2 and 3: lower risk,
each is independently testable in isolation, and the cost -- the same join computed
twice inside one Kafka Streams application -- is genuine but small at this scale (an
extra sub-topology reading the same input topics, not an extra network round-trip).

## Consequences

- If FR13 (SLA/anomaly detection) or any future slice needs a third event type off
  this same join, this pattern doesn't scale gracefully -- a fourth, fifth join
  starts to add real duplicated read cost. If that need arises, it's worth revisiting
  option 2 with an actual broker on hand to verify the binder's multi-output
  serialization behaves as expected, rather than defaulting to "one more function"
  indefinitely.
- Each processor writes its "published" marker to the state store *before* sending
  the event (at-least-once processing, the binder's default). A crash between those
  two steps can permanently lose a notification for that delivery, since the marker
  already prevents a retry from firing it. `exactly_once_v2` processing guarantees
  would close this window; not configured here -- accepted as a known limit for this
  slice rather than solved.
- The "published" markers in both state stores are never cleared. They grow
  unbounded until FR14 (delivery completion) exists and can clean up a completed
  delivery's entries. Not a problem at coursework scale, but worth flagging as a
  known gap for whoever picks up FR14.