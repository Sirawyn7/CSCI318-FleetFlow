

0014 tracking status read model · MD
# ADR 0014 — Tracking's order status endpoint and ETA read model

**Status:** Accepted
**Owner:** Landon
**Date:** 2026-10-09

## Context

UC8's explainer (FR16, notification-service) calls
`GET /deliveries/order/{orderId}/status` on Tracking. Its README documents that path as
a *proposed* contract; Tracking's `api/` package was empty, so every explanation said
"Tracking data unavailable". The ETA that FR11 computes lives only in Kafka Streams
state (RocksDB stores plus the `DeliveryEtaUpdated` topic), neither of which is
queryable over REST.

## Options considered

1. **Interactive queries against the Streams state store.** No duplicate data, but the
   store is keyed by courier, holds only a "last ETA" marker, and is local to one
   instance. It would couple the REST layer to Streams internals.
2. **Project `DeliveryEtaUpdated` into a JPA read model** (chosen). Tracking consumes
   its own published event with a plain-binder consumer and keeps the latest ETA per
   delivery in `delivery_eta_view`.
3. **Make Notification consume `DeliveryEtaUpdated` instead.** Rejected: the UC8 branch
   already settled on REST for the explainer, and the outline's matrix lists the ETA
   event as an inferred Notification edge only.
## Decision

- `delivery_eta_view` (keyed by deliveryId) is a derived read model, not
  source-of-truth. It stores `estimatedArrival` and `observedAt` (the event's
  `occurredAt`). An older event never overwrites a newer one, because Kafka does not
  guarantee ordering across restarts or rebalances. Consumption is idempotent via the
  `processed_events` ledger and uses a DLQ like Tracking's other consumers.
- `GET /deliveries/order/{orderId}/status` finds the newest `Delivery` for the order
  (Delivery is the source of truth for the order-to-delivery mapping) and joins the view.
  Response: `{orderId, estimatedArrival, riskFlags, updatedAt}`; unknown order is 404.
- **`riskFlags` is always `null` in this slice.** The UC8 contract defines null as
  "risk information unavailable" and `[]` as "no risk flags". Returning `[]` before any
  risk source exists would let the explainer wrongly tell a customer there is no risk.
  Filling it in is a follow-up once FR13 (at-risk detection) is merged and its storage
  is settled.
## Consequences

- FR16 now reports a real ETA and its freshness. Until FR13 lands it still says risk
  data is unavailable.
- This is the *latest* value only. A persisted, queryable ETA/risk history (flagged in
  the FR11-FR14 ADRs) remains separate, unclaimed work.
- The view is never cleaned up when a delivery completes; that belongs with the
  completion/state-store lifecycle work (FR14).

Claude finished the response
