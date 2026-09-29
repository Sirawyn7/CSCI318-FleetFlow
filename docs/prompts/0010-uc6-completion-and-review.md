# Prompt 0010 - Complete and verify UC6 delivery monitoring

**Date:** 2026-09-29
**Used for:** UC6 FR11-FR13 implementation; proposed ADR 0013; report Section 5.
**Why this is worth citing:** The implementation review identified gaps in the existing ETA and delivery-snapshot handling while adding windowed risk detection. This record connects the task, constraints, verification and human review responsibilities.

## Implementation prompt (edited for clarity)

A concise restatement of the request and session context, edited for clarity.

```text
Complete UC6 (FR11, FR12 and FR13) on this branch. Check what we already have for ETA and geofencing and fix anything that is missing. Then add the SLA breach and anomaly detection.

Also make sure tracking works properly when one courier has multiple deliveries, and handle duplicate or old GPS updates correctly. Keep everything within the current service structure.

Run the unit and real Kafka integration tests after the changes. Update the README and design notes as needed and give me a summary of what was changed.

If there are any decisions or reasoning that I need to make myself, clearly mark them and leave them for me to complete.
```

## Original request (exact wording)

The preceding conversation established the UC6 branch and its three functional requirements. The original follow-up was:

```text
ok do all of them make the prompts a meaning prompt , plus let me what you have done where human writing is required
```

FR11 and FR12 already had implementations when this request was made; FR13 was missing.

## Response (representative verbatim excerpt)

```text
FR11 and FR12 already have implementations and tests; FR13 is missing. I also found two gaps that affect monitoring: ETA uses processing time instead of the GPS timestamp, and the current snapshot holds only one delivery per courier. I’ll correct those while adding windowed risk detection, and keep human acceptance/rationale fields clearly marked for you to complete.
```

Human acceptance and rationale belong in ADR 0013 and the collaboration log. See `docs/uc6-implementation.md` for the handoff.
