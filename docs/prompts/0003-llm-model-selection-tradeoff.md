# Prompt 0003 — LLM model selection tradeoff (free tier + FR8 latency constraint)

**Date:** 2026-09-08
**Used for:** ADR 0005 (LLM model selection for the dispatch and explainer agents)
**Why this one's worth citing:** the prompt behind an accepted ADR (`docs/AGENTS.md`'s
first prompt trigger). Also demonstrates a deliberate prompting technique — stating
hard constraints (free access, fit to the specific use case) up front rather than
asking an open-ended "which LLM should we use," which is what let the response narrow
straight to a short, comparable option set instead of a generic model survey.

## Prompt (verbatim)

```
Provide a suggestion for the LLM model to be used - note that access should be free
and the LLM itself should match the use case of the system where relevant
```

## Response (verbatim excerpt — became ADR 0005's Options section)

```
Recommendation: Google Gemini (gemini-2.5-flash or gemini-2.0-flash) via the free
Google AI Studio API key.

Why this fits the use case specifically:
- Genuinely free, no credit card required. Google AI Studio issues API keys with a
  real no-cost tier — unlike OpenAI or Anthropic, which now require a funded account
  for any API access at all.
- Speed matches FR8's hard constraint. The dispatch agent has a locked 5-second
  timeout before falling back to rule-based assignment. The "flash" model line is
  built for low latency, which is the single most relevant property here — a slow
  model just means more of the demo runs the fallback path than the agentic path.
- Solid native tool/function-calling support, which both agents need — FR8's
  ReAct-style reasoning over tool calls, and FR16's tool calls to Order and Tracking
  REST endpoints.
- Maintained LangChain4j integration (langchain4j-google-ai-gemini), compatible with
  the LangChain4j 3.x line locked in by ADR 0003.

Backup option: Groq's free API running an open-weight model (e.g. Llama 3.3 70B).
Even lower latency than Gemini flash, but LangChain4j has no dedicated Groq module, so
it would go through the OpenAI-compatible client pointed at Groq's base URL — more
manual wiring, and open-weight tool-calling is somewhat less consistent than Gemini's.

Third option, only if you want zero external dependency: self-hosted via Ollama.
Fully free, no API key, works offline. Caution: three laptops with varying hardware
means inconsistent latency, risking the fallback path firing more often than the
agent path during the actual demo.
```
