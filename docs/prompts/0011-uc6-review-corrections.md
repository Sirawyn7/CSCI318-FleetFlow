# Prompt 0011 — UC6 review corrections

**Date:** 2026-10-08
**Used for:** ADR 0013; regression tests; Section 5 evidence
**Why this one is worth citing:** Multi-turn refinement after review rejected the earlier implementation and challenged assistant-authored acceptance. Human acceptance of these corrections remains pending.

## Prompt (verbatim excerpt from the review request)

> `docs/uc6-implementation.md` is a standalone implementation doc — please remove it and fold anything missing into the ADR (rationale), code comments (behavioral detail), and the log (pointer), consistent with the convention from the last two PRs.
>
> Happy to re-review once the process question is answered and 1–3 are fixed. can you fix these problem

## Response (representative verbatim excerpt)

> I’ll persist arrival on the delivery so new snapshots exclude it, and add a durable risk-state marker to suppress alerts while that snapshot update propagates. Recovery will use one transaction per courier and continue past failed rows. These changes stay within Tracking and use the existing `DeliveryArrived` event.
