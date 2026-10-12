# FR9 and cancellation merge handoff

Proposed order: merge the notification/cancellation fix first, then rebase FR9 onto it.
Author agreement is pending; GitHub repository access was unavailable during this review fix.

The FR9 rebase must reconcile these behaviours, not just conflict markers:

- Reservation counting must apply `a.cancelled = false` to both clauses:
  `(a.courierId = :courierId and a.loadAcknowledged = false) or
  (a.previousCourierId = :courierId and a.previousLoadAcknowledged = false)`.
  Parenthesize the whole OR expression before combining it with cancellation.
- DeliveryExceptionService must skip cancelled assignments, including retry/escalation
  paths, and check cancellation under the assignment lock before reassignment commits.
- Fleet owner lookup and `reassign()` must treat a cancellation tombstone with a null
  courier ID as terminal; a late reassignment must not restore load or dereference null.
- Keep exactly one Assignment `@Version` field (the cancellation branch's `Long`).
- Update FR9's ADR and requirements to state that Notification consumes OrderReassigned.
  Notification's README now identifies the publisher as implemented on the FR9 branch.

Regression checks after reconciliation: cancelled current/previous reservations do not
count; cancel-before-reassign and reassign-before-cancel release load exactly once;
cancelled assignments never publish a new OrderReassigned; null-owner tombstones remain
terminal; duplicate cancellation/completion/reassignment events do not change load twice.

Run `mvn clean install` and `mvn test "-Dtest=!*IntegrationTest"`. On a Docker-enabled
machine, run `mvn test "-Dtest=*IntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dkafka.integration=true"`
and confirm that the Kafka tests actually ran with zero skipped tests before merging.
Testcontainers can access Docker outside the sandbox on this review-fix machine.
The focused notification run passed all 14 tests with zero skips, including the Kafka
integration test and the legacy file-backed H2 schema upgrade regression.
Fleet's CourierLoadConsumerIntegrationTest (2 tests) and Dispatch's
OpenOrdersConsumerIntegrationTest (2 tests) also passed against Kafka with zero skips,
covering the new cancellation paths. Order's opt-in Kafka test passed separately.
Full reactor `mvn install` passed on 2026-10-12, including the remaining Kafka suites.
The default run skipped Order's opt-in test only; its separate run had zero skips.
`mvn clean install` could not proceed because Windows denied deletion of the root
`target` directory; the successful reactor run omitted `clean`.

Next follow-up: Tracking must consume OrderCancelled, stop monitoring cancelled deliveries
and suppress subsequent delivery notifications. Order status updates also remain outstanding.
