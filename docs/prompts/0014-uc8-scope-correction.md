# Prompt 0014 — UC8 Notification Service implementation

**Date:** 2026-10-07
**Used for:** UC8 (FR15–FR16), [ADR 0015](../decisions/0015-uc8-isolated-notification-integration.md), and report Section 5.
**Why this one's worth citing:** The user corrected the assistant's scope to keep UC8 separate from other services being developed on different branches.

## Decision

Implement UC8 within Notification using an in-app inbox and a conversational status
explainer. Use existing event contracts and configurable Order/Tracking REST clients.
Connect the other branches later. This decision is recorded in accepted ADR 0015.

## Prompt (verbatim)

With `docs/requirements-outline.md` as the reference:

```text
can you UC 8 - FR15 and 16
```

## Follow-up correction (verbatim)

```text
ok what i sadi is do UC8 but as much you can , other ucs are gettgin done in other branches, we will connec ti tlater
```

## Response (verbatim excerpt)

```text
Got it—UC8 only (FR15 and FR16). I’ll build the notification service against the existing event contracts, keep Order/Tracking REST connections configurable, and test with mocked responses. I won’t modify other UCs; I’ll document integration gaps for connecting the branches later.
```

**Outcome:** UC8 was implemented without changing the other services. All 19 tests
passed, including the real Kafka integration test. Live service integration and
Gemini testing remain for later. See the [UC8 guide](../../notification-service/README.md)
for implementation and test details.
