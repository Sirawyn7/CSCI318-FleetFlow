# Prompt 0010 — UC7 delivery completion and proof

**Date:** 2026-10-05
**Owner:** Tenzin
**Used for:** UC7 / FR14 in Tracking Service; ADR 0013; Report Section 5.
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

Implemented REST completion/history, persisted proof, retry protection and Kafka publication. All 34 local tests and the FR14 Kafka integration test passed. The design and limitations are recorded in [ADR 0013](../decisions/0013-FR14-completion-proof.md), which is marked Accepted with Tenzin as owner. Its decision wording was drafted with AI assistance.
