# Prompt 0015 — UC4 review corrections

**Date:** 2026-10-12
**Used for:** [ADR 0017](../decisions/0017-uc4-durable-reassignment-guard.md) and report Section 5.
**Why this one's worth citing:** Human review caught a concurrency gap in the AI-assisted implementation: locking prevented duplicate reassignments, but still allowed duplicate paid or quota-limited agent calls. The review prompted a change to when the attempt is claimed.

## Context

The offline-event consumer and recovery sweep could both call the agent before
either saved the attempt. The review also found unnecessary escalation locks and
repeated offline signals overwriting the reason that no replacement was available.

## Prompt (verbatim)


The lock stops a double reassignment, but it costs two Gemini calls against the free-tier quota, and the winning recommendation is nondeterministic. Claim the attempt (set `exceptionAttemptedAt` under the row lock in a short transaction) before calling the agent. ADR 0017 already states that the attempt consumes the budget even with no outcome, so this doesn't change the guard's semantics.


## Response (verbatim)

The attempt is now claimed under the row lock before the agent call, and escalation queries select only due rows. I’m adding regression tests for overlapping offline triggers and the lost no-candidate reason.

## Outcome and verification

The attempt is now saved under lock before calling the agent, so overlapping
triggers share one attempt. Escalation selects only due deliveries, and repeated
offline signals preserve the detailed no-candidate reason for operators. The README
UC4 section was also moved and shortened, with design details retained in ADR 0017.

**234 regression tests and the real-Kafka `DeliveryExceptionIntegrationTest` passed
on 2026-10-12.** Added coverage checks overlapping triggers while the agent is still
running, preservation of the operator reason and the escalation cooldown boundary.

This records the review follow-up. The original implementation prompt is unavailable.
