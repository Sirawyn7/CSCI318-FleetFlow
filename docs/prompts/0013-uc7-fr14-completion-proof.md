# Prompt 0013 — UC7 delivery completion and proof

**Date:** 2026-10-05
**Owner:** Tenzin
**Used for:** UC7 / FR14 in Tracking Service; ADR 0014; Report Section 5.
**Why this one's worth citing:** The task required choosing how to record delivery proof within the existing Tracking service. The assistant proposed a courier-supplied summary and confirmation because GPS history was not yet persisted.

## Prompt (verbatim user revision)

Tenzin supplied this wording after implementation to replace the earlier prompt versions.

```text
Please complete UC7 (FR14) in the Tracking Service. Add support for completing a delivery by saving the delivery proof, route summary, and relevant timestamps. Add the REST endpoints needed to save and retrieve this information, and publish a `DeliveryCompleted` event when the delivery is completed.
Follow the existing project structure and patterns, and add the necessary tests to make sure everything works correctly.
```

## Earlier implementation response (verbatim excerpt)

```text
Yes—UC7 contains only FR14. I’m adding completion and history REST endpoints, persisted proof and timestamps, and the `DeliveryCompleted` event. I’ll use a courier-supplied route summary and confirmation, since the current code doesn’t persist GPS history.
```

## Outcome

Implemented REST completion/history, persisted proof, retry protection and Kafka publication. All 34 local tests and the FR14 Kafka integration test passed. The design and limitations are recorded in [ADR 0014](../decisions/0014-FR14-completion-proof.md), which is marked Accepted with Tenzin as owner. Its decision wording was drafted with AI assistance.

## Review follow-up — 2026-10-09

Verbatim excerpts from the user's review and instruction:

```text
→ Publish the internal snapshot first, external event last, so a failure can only roll back before anything externally visible has gone out.
```

```text
 fix these things, Decision 0013 and prompt 0012 belongs to fr 13branch
```

The assistant asked whether to reconcile with local FR13 now and establish
FR13 → FR14 order. The user's verbatim reply:

```text
Yes, reconcile with FR13 now
```

Generated changes: internal-first publication in bootstrap and completion; FR13's
courier list retained with arrived/completed deliveries excluded; shared courier
locking; regression tests for rollback/replay and concurrent-order preservation.
ADR 0013 and prompts 0010–0012 remain FR13's; this FR14 record is prompt 0013,
linked to ADR 0014.

Verification on 2026-10-09: full reactor `package` succeeded with 175 non-Docker
tests passing. `Fr14EndToEndIntegrationTest` also passed against real Kafka through
Testcontainers. These are separate from the historical implementation results above.
