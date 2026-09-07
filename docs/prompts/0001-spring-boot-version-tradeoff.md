# Prompt 0001 — Spring Boot 4 vs 3.5 version pin tradeoff

**Date:** 2026-09-07
**Used for:** ADR 0003 (Spring Boot / Spring Cloud version pin)
**Why this one's worth citing:** demonstrates a deliberate prompting/workflow technique —
rather than silently picking the newest dependency version off Maven Central, the
assistant surfaced the tradeoff as a structured choice and paused for human sign-off
before writing any POM. The chosen option's full rationale (below) also became the raw
material for ADR 0003's Context/Options sections. See `docs/AGENTS.md`'s third prompt
trigger ("a prompt demonstrating a deliberate prompting technique worth explaining").

## Prompt (verbatim — assistant's clarifying question to the team member)

```
Spring Boot is currently on major version 4 (4.1.1, GA since ~Aug 2026), a jump from
the 3.x line this course likely expected. Which should the parent POM pin to?

Option A — Spring Boot 3.5.16 + Spring Cloud 2025.0.x
Last 3.x line. Mature, widest tutorial/StackOverflow coverage, safest for a 3-person
team on a fixed 6-week timeline. Spring Cloud Stream, Testcontainers, and LangChain4j's
Spring Boot integration are all long-battle-tested here. Recommended for coursework
where architectural correctness matters more than using the newest release.

Option B — Spring Boot 4.1.1 + Spring Cloud 2025.1.2
Current latest GA. LangChain4j now ships a dedicated boot4-starter and Testcontainers
has deeper native integration in Boot 4, but both ecosystems are newer here (an open
LangChain4j issue flagged Boot 4 incompatibility before the boot4-starter existed) —
more risk of hitting undocumented rough edges mid-project.
```

## Response (verbatim — team member's answer)

```
Sprint boot 4.1.1
The deciding factor is that debugging time on this project is not fungible with
anything. Six weeks, three people with other subjects, and a report that needs
writing. An afternoon lost to an undocumented Boot 4 interaction between LangChain4j
and Spring Cloud Stream is an afternoon not spent on the DDD justifications or the
stream topology — and it earns you nothing, because no marking criterion rewards
framework recency.

The LangChain4j signal is the specific one I'd weight. Your two agentic components are
graded, they're the most novel thing you're building, and they're scheduled for week
7 — right when a rough edge would hurt most. A dedicated boot4-starter existing is not
the same as the integration being well-trodden; an open incompatibility issue
predating that starter says the ecosystem is still settling. Steven and Landon would
be debugging framework integration rather than agent design, on the exact deliverable
that differentiates your project.

The tutorial-coverage argument is worth more here than it usually is, too. Three
students hitting Spring Cloud Stream binding config for the first time benefit
enormously from Stack Overflow answers that match their version. On 4.1.1 you'd be
reading answers written for 3.x and guessing at what transfers.

Two things to do alongside this:

Write it up as an ADR. "We chose the mature version over the current one because
timeline risk outweighs recency on a fixed-deadline project" is a legitimate
engineering judgment with a real rejected alternative. It's also honest — you're not
claiming 3.5.16 is technically superior, you're saying the risk profile suits the
constraints. That reads well in Section 5.

Pin exactly and verify compatibility at implementation time. The plan already says not
to copy version numbers from memory. Confirm 3.5.16 against Spring Cloud 2025.0.x on
Maven Central, and confirm the LangChain4j Spring Boot starter version that pairs with
it — that's the one most likely to surprise you.
```

**Note on the literal text above:** the response's opening line names "4.1.1" but every
argument that follows, and the closing instruction ("confirm 3.5.16 against Spring
Cloud 2025.0.x"), argues for and directs implementation of Option A (3.5.16). Treated as
a labeling slip rather than the actual instruction — the assistant proceeded on 3.5.16 /
Spring Cloud 2025.0.3 and flagged the discrepancy back to the team member rather than
silently resolving it either way.
