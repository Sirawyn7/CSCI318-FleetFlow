# Prompt 0004 — Self-review of the CourierRegistered slice plan against convention

**Date:** 2026-09-08
**Used for:** the CourierRegistered reference vertical slice plan (Fleet → Dispatch,
branch `slice/courier-registered`)
**Why this one's worth citing:** the assistant's first plan submission was rejected, not
accepted outright — `docs/AGENTS.md`'s second prompt trigger. Also the same deliberate
technique as Prompt 0002 (re-check an already-drafted plan against convention before
touching any code), reused a session later on an unrelated plan — worth citing alongside
0002 as evidence it's a repeatable practice rather than a one-off. See `docs/AGENTS.md`'s
third prompt trigger.

## Prompt (verbatim)

```
Please review this plan for logical and technical consistency, ensuring it adheres to
existing convention
```

## Response (verbatim excerpt — the three corrections the self-review produced)

```
1. Fix "both READMEs" → single README.md, and be specific about updating the existing
   stale Kafka paragraph + module map row.
   [Factual error: the repo has one root README.md, no per-service READMEs — verified
   by Glob earlier in the same session, so the plan text contradicted the assistant's
   own prior finding.]

2. Fix the polling/idempotency test description to include a grace-period re-check
   after first reaching count=1, not just an immediate assert.
   [The originally-planned test would poll until courier_availability_view/
   processed_events each showed one row, then assert immediately — which could pass
   merely because the duplicate message hadn't been consumed yet, not because it was
   actually deduped. Rewrote it to wait through a further fixed grace window and
   re-assert the counts hadn't grown before treating dedup as proven.]

3. Fix the dangling cross-reference — "verification items 6 and 7" referred to the
   original scaffold plan's numbering, not this plan's own differently-numbered
   Verification section, which would confuse a reader working from this plan file
   alone. Reworded to describe what the test covers directly instead of citing a
   number from a different document.
```

**Note on what prompted this:** the plan — full layer-by-layer designs for Fleet's
producer side and Dispatch's consumer side, plus the Testcontainers integration test —
had already been drafted and was about to be submitted via `ExitPlanMode` when the
rejection came in. All three corrections were caught only once asked to re-check the
already-written plan against the codebase and prior findings, not during the original
drafting pass.
