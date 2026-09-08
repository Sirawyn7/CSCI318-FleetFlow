# ADR 0005 — LLM model selection for the dispatch and explainer agents

**Status:** Accepted
**Owner:** Steven
**Date:** 2026-09-08

## Context

`requirements-outline.md` §12.1 has flagged LLM model selection as an open decision
since Week 4, blocking FR8 (agentic dispatch optimisation) and FR16 (agentic delivery
status explainer) — both scheduled to start in Week 7. It is still unresolved: there is
no ADR or config for it anywhere in the repo, only the unrelated Spring Boot/LangChain4j
*starter-line* pin in ADR 0003. This ADR closes that gap.

Two hard constraints from `docs/AGENTS.md` shape the options:

- **Budget is $0.** This is coursework, not a funded product — whatever is chosen must
  be usable by all three team members without a paid account.
- **The two agents have different needs.** FR8's dispatch agent is event-triggered,
  ReAct-style (reason over fleet/order tool calls, decide, justify), and is bound by the
  **locked 5-second timeout** before falling back to rule-based assignment (AGENTS.md
  locked decision 3) — so response latency directly determines how often the agent path
  actually fires versus falling back. FR16's explainer agent is conversational
  request-response with no timeout constraint, so fluency matters more than raw speed
  there. Both use LangChain4j `AiServices` with `@Tool`-annotated methods, so the model
  must have solid, consistent function/tool-calling support — a model that produces
  malformed tool calls turns into guardrail/retry code neither agent's slice budgeted
  time for.
- Must integrate with LangChain4j's 3.x line (`langchain4j-spring-boot-starter`), not
  the Boot-4-only `-spring-boot4-starter`, per ADR 0003.

## Options considered

1. **Google Gemini (`gemini-2.5-flash` or `gemini-2.0-flash`) via Google AI Studio.**
   Free API key, no credit card or billing account required, with a daily
   request/token allowance well above what a coursework demo needs. The "flash" line is
   built for low latency, which is the most relevant property for FR8's 5-second
   window — a slower model just means more of the demo runs the rule-based fallback
   than the actual agent. Has mature native function-calling support and a maintained
   LangChain4j integration (`langchain4j-google-ai-gemini`, 3.x-compatible).

2. **Groq free API running an open-weight model (e.g. Llama 3.3 70B).** Free tier, and
   generally the fastest inference available for free — potentially an even better fit
   for the 5-second constraint than Gemini flash. Tradeoff: LangChain4j has no dedicated
   Groq module, so it would go through the OpenAI-compatible client pointed at Groq's
   base URL — more manual wiring — and open-weight tool-calling tends to be less
   consistent than Gemini's, which matters for both agents' `@Tool` reliability.

3. **Self-hosted via Ollama** (e.g. Llama 3.1 8B, Mistral 7B). Fully free, no API key,
   works offline — the only option with zero external dependency, which also means
   nobody needs to manage a shared or personal API key. Tradeoff: latency and
   reliability depend on each team member's own hardware, with no shared floor. Given
   FR8's fallback exists precisely to absorb slow/failed agent calls, an inconsistent
   local model risks the agent path firing rarely during the actual demo — undermining
   the point of having built it. Three different laptops also means three different
   experiences of the same "agent," which is awkward to explain in the report.

Not considered: OpenAI and Anthropic's APIs, both of which now require a funded
billing account for any access — ruled out by the $0 constraint before evaluating
capability.

## Decision

Going with option 1: Gemini, specifically gemini-2.0-flash

Reasoning: it's free without needing to put a card on file anywhere, which matters
because none of us want to be the one explaining why the demo broke because someone's
trial credits ran out. The bigger reason though is speed, FR8 has that hard 5 second
timeout before it falls back to the rule-based path, so if we pick something slow we
basically end up demoing the fallback the whole time instead of the actual agent, which
kind of defeats the point of building FR8 in the first place. The "flash" models are
built for exactly this — fast responses over deep reasoning, which is fine for us since
the agent's job is closer to "pick a sensible courier quickly" than "solve a hard
problem slowly."

## Consequences

We're now relying on a third-party API being reachable during the demo — no internet
means no dispatch agent, just the rule-based fallback. Worth a dry run on whatever wifi
we're demoing on beforehand instead of assuming it'll be fine on the day.

Free tier = rate limits. Should be nowhere near what a demo needs, but if someone
load-tests FR8 by hammering it with a script we could get throttled mid-testing, so
don't go overboard with automated runs against the real API
