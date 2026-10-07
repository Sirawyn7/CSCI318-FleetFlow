# ADR 0013 — UC8 inbox and deferred service integration

**Status:** Accepted
**Owner:** Tenzin
**Date:** 2026-10-07

## Context

The user explicitly limited work to UC8 because other use cases are being developed
on separate branches and will be connected later. Current event contracts have no
recipient ID; near-destination events contain only a delivery ID. Tracking does not
yet expose the REST status endpoint needed by FR16. No external notification channel
is configured.

## Decision

We will implement UC8 independently within the Notification service, using an in-app
inbox for customer notifications and configurable REST clients for the status
explainer. We will use the existing event contracts and connect Order and Tracking
once their branches are ready.

## Rationale

Other team members are developing those services separately. Keeping this work
within Notification avoids overlapping changes and lets us test UC8 without waiting
for integration. An in-app inbox demonstrates event-driven notifications without
requiring an external email or SMS provider. Pending notifications remain stored
when Order data is unavailable, and the explainer clearly identifies missing
information instead of inventing a delivery status.

## Trade-off

Passing the isolated tests does not prove the complete delivery flow works. We still
need to agree on the Tracking REST response and test the services together after
merging.

## Implementation details

1. Use a persisted in-app inbox as the concrete dispatch channel. Render the five
   required lifecycle templates and publish the existing NotificationSent event with
   channel IN_APP. Email/SMS adapters can be added separately if required.
2. Persist incoming messages and processed-event IDs together. Build a local
   delivery-to-order correlation from existing DeliveryStarted, DeliveryCompleted and
   DeliveryAtRisk messages. Resolve recipients through Order REST in a later worker
   transaction. A missing link or unavailable Order leaves the message pending.
3. Commit inbox delivery before publishing its audit event. Reuse the notification
   UUID as the audit envelope ID. Broker/DB atomicity is not claimed: audit replay can
   duplicate an event after a crash; its identity remains stable.
4. Keep Order and Tracking behind a Notification-owned DeliveryDataPort. Match the
   current Order response; document a proposed Tracking REST DTO/path for later
   agreement. Do not modify another service or the shared event contracts here.
5. Give the explainer only two no-argument, read-only tools bound to the selected
   order. Persist conversations and refresh source facts on every turn. Bound tool
   rounds and output length, and provide an explicit factual fallback on model failure.

## Consequences and integration work

The UC8 slice is testable without the other branches using mocked REST responses and
real Kafka. Full live behaviour depends on the other branches producing their
existing events and the Tracking owner confirming/implementing the documented REST
contract. Pending notifications retry indefinitely until their prerequisites arrive;
operators can inspect pending rows and replay malformed events from the Kafka DLQ.

The in-app channel, extra infrastructure tables, and proposed Tracking DTO should be
reviewed by the team at integration. Coordinator approval is not implied by this
decision. No other bounded context is changed.
