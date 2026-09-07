# ADR 0003 — Spring Boot / Spring Cloud version pin

**Status:** Accepted
**Owner:** Steven
**Date:** 2026-09-07

## Context

Commit 1 pins the parent POM's Spring Boot version, which cascades into the Spring
Cloud BOM, the eventual LangChain4j starter family (§12.1 — still open), and the
Testcontainers integration (§11). As of this week Spring Boot's current GA is major
version **4** (4.1.1) — a jump from the 3.x line most existing Spring tutorials and
course material still target. This is a build-tooling call that affects every
subsequent commit and all three service owners, not something to lock in silently.

## Options considered

1. **Spring Boot 4.1.1 + Spring Cloud 2025.1.2 (Oakwood)** — current GA, newest
   features. LangChain4j's Boot 4 support (`langchain4j-spring-boot4-starter`) is newer:
   an open GitHub issue (`langchain4j/langchain4j#4268`) flagged Boot 4 incompatibility
   with the original starter before the dedicated Boot-4 starter existed. Testcontainers
   v2, which pairs with Boot 4's deeper `@ServiceConnection` integration, ships breaking
   changes (module renames, JUnit 4 removal) around the same timeframe. Tutorial/
   StackOverflow coverage for Boot 4 is thin, since it only reached GA in August 2026.

2. **Spring Boot 3.5.16 + Spring Cloud 2025.0.3 (Northfields)** — the current tip of the
   3.x line, still receiving patch releases (latest patch dated 2026-08-20 per Maven
   Central metadata). Every mandated dependency relevant to this project (Spring Cloud
   Stream, Testcontainers via the Boot BOM) is mature here. LangChain4j's original
   `langchain4j-spring-boot-starter` targets this line without the Boot-4-specific
   caveats above.

Both are real Maven Central releases as of this session — versions were checked against
raw `maven-metadata.xml`, not assumed from memory.

## Decision

We're going with option 2: Spring Boot 3.5.16 + Spring Cloud 2025.0.3, not the
newer 4.1.1 + 2025.1.2 pair.

Reasoning: none of us have three spare afternoons to burn on a Spring Boot 4
edge case that Stack Overflow doesn't have an answer for yet, especially not
in week 7 when we're trying to get the dispatch agent and the explainer agent
working at the same time. 4.1.1 only just went GA and LangChain4j's Boot 4
starter is brand new, there was an open GitHub issue about it not
working before that starter existed.

## Consequences

Means we're intentionally not on the newest Spring Boot, which is a bit
against the "use current tech" instinct, but there's no marks for that and
it's not what this ADR is optimising for.
