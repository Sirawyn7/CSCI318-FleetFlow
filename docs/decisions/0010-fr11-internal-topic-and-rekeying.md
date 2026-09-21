# ADR 0010 — FR11's internal active-delivery topic, and re-keying CourierLocationUpdated

**Status:** Accepted
**Owner:** Landon
**Date:** 2026-09-20

## Context

FR11 (`requirements-outline.md` §4, UC6) recalculates ETA via a Kafka Streams
stream-table join: a `KStream` of `CourierLocationUpdated` pings joined against a
`KTable` of each courier's active delivery (destination + average speed). Two
questions came up building this that no existing document settled.

**First:** where does the `KTable` side of the join come from? Kafka Streams
materializes a `KTable` from a compacted topic, not from a JPA table directly. The
`deliveries` table (§8) is source-of-truth, written once per delivery at bootstrap
time; the topology needs an equivalent, up-to-date-per-courier view it can read as a
`KTable`, keyed the same way as the location stream (`courierId`).

**Second:** `CourierLocationUpdated` (FR10, already shipped) was published with no
Kafka record key. A stream-table join requires both sides to be co-partitioned on the
same key — an unkeyed topic can't be joined against a `courierId`-keyed `KTable`.

## Options considered

**For the KTable source:**
1. **A new internal topic**, published by Tracking's own bootstrap step
   (`DeliveryBootstrapService`), carrying just what the join needs
   (`deliveryId`, destination, average speed), keyed by `courierId`. Materialized as
   a `KTable` by the topology itself.
2. **Query the `deliveries` JPA table directly from inside the Streams topology**,
   per location ping. Rejected — this is hot-path state read on every single ping
   (FR10's `CourierLocationUpdated` is explicitly high-frequency), and hitting a
   relational database that often from a Streams processing thread adds I/O latency
   into the topology for no benefit; the same source-of-truth-vs-derived-state
   distinction §8 already applies to Dispatch's `courier_availability_view` applies
   here too.

**For the keying gap:**
1. **Re-key the already-shipped `CourierLocationUpdatedEventPublisher`** (FR10) to
   set the Kafka record key to `courierId`. Touches merged code from an earlier PR.
2. **Have Tracking re-publish a re-keyed copy** of every location update on its own
   internal topic instead. Avoids touching FR10's code, but means every
   high-frequency location ping gets double-published (once by Fleet, once again by
   Tracking) purely to add a key that should have been there from the start.

## Decision

Going with option 1 for both: a new internal topic
(`fleetflow.tracking.internal.active-delivery-by-courier`) for the `KTable` side, and
re-keying FR10's publisher directly rather than working around it.

The internal topic is deliberately named outside the
`fleetflow.<context>.<event>` catalogue convention (§6) — it never crosses a
bounded-context boundary, it exists purely to materialize this one topology's
`KTable`, so it isn't a domain event in the usual sense and no other service should
ever consume it.

Re-keying FR10's publisher was the more honest fix even though it touches merged
code — the alternative (re-publishing a duplicate keyed stream) doubles the
write/read volume on FleetFlow's highest-frequency event permanently, to work around
a keying decision that should have been made when FR10 was built. A one-line change
to an existing publisher is cheaper than that, indefinitely.

## Consequences

Any consumer relying on `CourierLocationUpdated`'s Kafka partition assignment
behaviour (none currently do) would see records redistributed across partitions
differently after this change, since they now route by key instead of round-robin.
No such consumer exists today. Future high-frequency events published without a
deliberate key should be considered against this precedent before shipping, since
retrofitting a key later means touching already-merged code, as happened here.