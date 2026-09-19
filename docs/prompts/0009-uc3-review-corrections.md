# Prompt 0009 — UC3 review corrections

**Date:** 2026-09-19
**Used for:** ADR 0010 implementation refinement; report Section 5
**Why this one is worth citing:** The review modified the earlier AI-generated transaction and load-projection design.

## User prompt (edited for clarity)

The following restates the original request using the attached review; it is not a verbatim transcript.

```text
Please address the attached UC3 code review findings. Move the dispatch agent call outside database transactions and revalidate the selected courier in a short transaction before committing an assignment. Handle load acknowledgements without a matching local assignment by preserving Fleet's authoritative load and logging the missing reservation. Make shared zone boundaries unambiguous, and validate load-event values before checking their version for staleness.

Also address the registration race, duplicate-event lock contention, agent HTTP timeout handling, and test-coverage issues identified in the review. Preserve the existing service boundaries and agent-first rules fallback, add regression coverage, and run the Fleet and Dispatch tests, including Kafka integration tests. Report any remaining limitations.
```

## Attached review (verbatim)

```text
Ran a full build + review pass (compile, unit tests, static review across the diff). Unit tests pass (95/96 — the one failure is gps-simulator's GpsPingSchedulerTest, unrelated to this PR and pre-existing on main). Testcontainers/Kafka integration tests could not be run to completion in this environment, so those haven't been confirmed green on either side of this diff — please run them before merging.

The agent-guardrail design (bounded tool access, independent output validation, justification length caps, no writes exposed as tools) is solid and well documented — no changes needed there.

Required before merge
1. AssignmentService.attemptAssignment holds a fleet-wide lock across the synchronous 5s agent call
dispatch-service/.../application/AssignmentService.java:50
couriers.findAvailableForUpdate() locks every available courier row, then agent.attempt(...) blocks the same transactional thread for up to 5s on the Gemini round trip before anything commits or releases. Any concurrent OrderPlaced serializes behind this for the same window — throughput collapses to ~1 order per 5s fleet-wide under load. Worse, CourierAvailabilityProjectionService/CourierLoadProjectionService now lock the same rows and hard-throw (not skip) if they can't get them in time, so a slow agent call cascades into DLQ churn on three unrelated topics, not just latency.
→ Build the read-only snapshot and call the agent outside any row lock; open a short, narrowly-scoped transaction only for the chosen courier at commit time.

2. CourierLoadProjectionService.apply has no reconciliation path for orphaned load acknowledgements
dispatch-service/.../application/CourierLoadProjectionService.java:30
Fleet publishes CourierLoadChanged for every OrderAssigned/DeliveryCompleted it processes, including orders that predate this feature, were seeded, or lost their Dispatch Assignment row to an earlier rollback. Dispatch throws IllegalStateException("Load received before assignment committed"), and with enableDlq: true this permanently DLQs and stalls load projection for that courier. docs/uc3-implementation.md names this as a known limitation but there's no degrade-gracefully path.
→ Skip-and-log instead of throwing when no matching assignment exists, at minimum for the historical-data case.

3. Zone boundaries double-claim their shared edge
dispatch-service/.../application/OperatingZones.java:30
Bounds.contains() is inclusive on both min and max. ZONE_A's max-latitude and ZONE_B's min-latitude are both -34.0 in the sample config, so a pickup exactly on that edge is eligible for both zones with no tie-break.
→ Make one bound exclusive (e.g. < maxLatitude), or add a tie-break.

4. CourierAvailabilityView.updateLoad swallows a malformed negative version instead of rejecting it
dispatch-service/.../infrastructure/persistence/CourierAvailabilityView.java:37
The staleness check (version < loadVersion) runs before the validity check (version < 0). For a courier with no prior load event (loadVersion defaults to 0), a CourierLoadChanged with version = -1 satisfies the staleness check and returns silently — the intended IllegalArgumentException never fires, and the event still gets marked processed.
→ Validate before checking staleness.

Worth fixing, not blocking
Lock acquired before idempotency check in CourierLoadProjectionService.apply (:24 locks, :26 checks processed.existsById) — inconsistent with the sibling methods in this same diff, which check first. Adds avoidable lock contention on retry storms.
Unlocked registration duplicate-guard race: CourierAvailabilityProjectionService's new guard in apply() (:299) uses findById while the sibling methods right next to it were upgraded to findForUpdate specifically for this kind of race. Two concurrent CourierRegistered redeliveries can both pass the guard and race on the primary key.
Agent thread pool can starve under a network partition: BoundedDispatchAgent (:270) calls task.cancel(true) on timeout, but the underlying Gemini HTTP call may not honor interrupts if it's blocked in I/O before its own client-side timeout starts. With a bounded 8-thread pool and AbortPolicy, a sustained Gemini outage can exhaust the pool permanently (until restart), surfacing as misleading "Agent busy" fallbacks instead of the real cause.
Weakened test assertion: CourierAvailabilityChangedConsumerIntegrationTest (:67) changed an exact processed_events count check to an existence check for only the new event — silently drops coverage of the CourierRegistered event's own processing.
Minor: CourierAvailabilityViewRepository.findByAvailableTrue() is declared but never called (dead code, AssignmentService uses findAvailableForUpdate() instead); CourierLoadProjectionService/CourierLoadChangedConsumerConfig hardcode the "CourierLoadChanged" event-type string instead of reading it off the envelope like every sibling consumer does.
Documentation
ADR 0010, docs/prompts/0008, README, and requirements-outline.md are all updated well. Missing: an ai-collaboration-log.md pointer entry for ADR 0010 — the project convention (see the FR7 entries above it) is that even a fully-ADR'd decision gets a short log entry, not just the ADR itself.


```

## Response (representative verbatim excerpt)

```text
The four required findings are valid. I’ll move the AI call outside the transaction, then recheck and reserve only the selected courier in a short transaction. I’ll also fix orphaned load acknowledgements, shared zone edges and negative-version validation, with regression tests for each.
```

The human acceptance and rationale remain for a team member to record in the collaboration log.
