# Prompt 0002 — Self-review of fleetflow-contracts field design against §6.1

**Date:** 2026-09-07
**Used for:** ADR 0004 (fleetflow-contracts: Published Language, not Shared Kernel)
**Why this one's worth citing:** demonstrates a deliberate prompting technique — asking
the assistant to re-check its own already-drafted implementation plan against existing
conventions *before* writing any code, rather than treating a plan as final once
approved-in-principle. The self-review caught two consumer-side gaps a first pass
missed, changing the actual event shapes that shipped. See `docs/AGENTS.md`'s second
prompt trigger (the assistant's first answer was modified, not accepted outright) and
third trigger (a deliberate prompting technique worth explaining).

## Prompt (verbatim)

```
Please review this plan for logical and technical consistency, ensuring it adheres to
technical and existing design conventions
```

## Response (verbatim excerpt — the two corrections the self-review produced)

```
Two corrections that came out of tracing the matrix, not the original scaffold plan
text (which didn't specify fields at all — this is the judgment call layered on top):

- No `AssignmentPath`/decision-path field on `OrderAssigned`. The plan document
  explicitly places "path used" in Dispatch's own `assignment_decisions` table (a
  Dispatch-owned audit concern), and no consumer in §6.1 (Tracking, Notification,
  Fleet, Order) needs it. Adding it to the wire event would be scope creep the module
  doesn't need — leave it in Dispatch's own DB.
- `DeliveryAtRisk` and `DeliveryCompleted` carry `orderId` and `courierId` directly,
  not just `deliveryId`. Their documented/inferred consumers — Dispatch
  (`DeliveryAtRisk`, to reassign) and Fleet (`DeliveryCompleted`, to decrement the FR6
  active-order count) — do not consume `DeliveryStarted` per the §6.1 matrix, so they
  have no existing `deliveryId → orderId/courierId` translation table of their own to
  resolve a bare `deliveryId` against. Without these fields the documented consumer
  literally cannot act on the event.
```

**Note on what prompted this:** the plan up to this point had already been drafted and
was about to be submitted for approval. The instruction to re-check it — rather than
proceeding straight to implementation — is what surfaced these two gaps; neither had
been caught by the initial drafting pass, which had specified event packages and names
correctly but had not traced every field against the consumer matrix in
`requirements-outline.md` §6.1.
