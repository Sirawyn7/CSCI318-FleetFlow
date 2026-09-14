# Prompt 0007 — Fleet load event ordering

**Date:** 2026-09-12
**Used for:** ADR 0008, FR4/FR6 implementation, Section 5 AI collaboration evidence
**Why this one's worth citing:** Captures the prompt behind accepted ADR 0008, meeting the accepted-ADR documentation trigger. The assistant introduced an order ledger while implementing FR6; the user did not explicitly request that design. Tenzin's decision and rationale are recorded in ADR 0008.

## Prompt (verbatim)

Initial scope:

```text
Lets to UC2, but leave FR5, as my teamate is doing it
```

Instruction to proceed after discussing FR4/FR6 and the branch:

```text
ok lets do it
```

## Response (verbatim, or a representative excerpt if long)

Assistant implementation update:

```text
Registration is already implemented. I’m adding profile management and a separate deactivation flag so FR5’s shift status stays independent.

For FR6, I’ll track which orders contribute to each courier’s load. This prevents duplicate events—and completion events arriving before assignment events—from corrupting the count. The existing ADR authorizes Fleet to consume assignment and completion events.
```

## Follow-up review (verbatim excerpts)

User:

```text
ok lets check the decison first
```

Assistant response excerpt (the decision was numbered 0006 at that point and subsequently renamed to 0008):

```text
For example, if Order A’s completion arrives before its assignment, the ledger remembers that A is completed. Its later assignment won’t add to the courier’s load.

**The tradeoff:** an extra table whose completed records must be retained for late-event protection.
```
