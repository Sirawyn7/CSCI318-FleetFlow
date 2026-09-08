# AI Collaboration Log

Running chronological log of AI-assisted decisions on this project, for Report Section 5 (AI-Driven Development Process).

**Rule (see `/docs/AGENTS.md`):** the assistant may capture raw material — what was prompted, what was generated — but the *decision and rationale* must be written by the team member who made the call. That judgment is where Section 5's marks are. Do not paste in an assistant-authored justification and call it done.

Target: roughly one entry per person per working session, ~15–25 entries by Week 11. Log it as part of your own slice's definition of done, not as a shared task to defer.

## Entry format

```
## YYYY-MM-DD — Short title of the decision (Name)
**Prompt/context:** What was asked of the assistant, and why.
**Proposed by agent:** What the assistant proposed or generated.
**Decision:** Accepted / Rejected / Modified.
**Rationale:** Why, in your own words. Reference the specific bounded-context,
DDD, or architectural principle at stake if there is one. If this decision has a
full ADR in `docs/decisions/`, name the specific file rather than saying "see the
ADR" — e.g. "see docs/decisions/0001-dispatch-open-orders-backlog.md".
```

---

## 2026-09-07 — Dispatch open-orders backlog (Steven)
**Prompt/context:** Asked the assistant to build a publisher/consumer matrix for the
event catalogue, since §6 only documented publishers. That exercise surfaced that
Dispatch had no table to hold open orders, despite FR8 having the dispatch agent
reason over "open orders" and fire on `OrderPlaced`.
**Proposed by agent:** Three options for where Dispatch's open-order data comes
from, written up in `docs/decisions/0001-dispatch-open-orders-backlog.md`.
**Decision:** Accepted option 2 — a derived `open_orders_view` projection, matching
the existing `courier_availability_view` pattern.
**Rationale:** See `docs/decisions/0001-dispatch-open-orders-backlog.md` for full
reasoning.

---

## 2026-09-07 — Fleet active-order count (Steven)
**Prompt/context:** The same publisher/consumer matrix exercise flagged that FR6
requires Fleet to track an active-order count against a capacity ceiling, but Fleet
had no field for it and wasn't documented as consuming any events at all.
**Proposed by agent:** Two options for where the count lives, written up in
`docs/decisions/0002-fleet-active-order-count.md`.
**Decision:** Accepted option 1 — an `active_order_count` column directly on
`couriers`, rather than a separate table.
**Rationale:** See `docs/decisions/0002-fleet-active-order-count.md` for full
reasoning.

---

## 2026-09-07 — Spring Boot version pin for the scaffold (Steven)
**Prompt/context:** Asked the assistant to scaffold the parent POM. It flagged that
Spring Boot's current GA is major version 4 (4.1.1), a jump from the 3.x line, and
asked which to pin to before writing any POM.
**Proposed by agent:** Two options in docs/decisions/0003-spring-boot-version-pin.md —
Boot 4.1.1 + Spring Cloud 2025.1.2, or Boot 3.5.16 + Spring Cloud 2025.0.3.
**Decision:** Accepted option 2 — Spring Boot 3.5.16 + Spring Cloud 2025.0.3.
**Rationale:** See docs/decisions/0003-spring-boot-version-pin.md for full reasoning
(timeline risk on a fixed 6-week/3-person project outweighs using the newest release,
particularly given the two graded LangChain4j agents are scheduled for week 7).

---

## 2026-09-07 — fleetflow-contracts: Published Language vs Shared Kernel (Steven)
**Prompt/context:** Implementing Commit 2 of the approved scaffold plan — a
zero-dependency `fleetflow-contracts` module carrying the 15 locked §6 events, shared
across all five services.
**Proposed by agent:** Module design plus a self-review against the §6.1 consumer
matrix, written up in `docs/decisions/0004-fleetflow-contracts-published-language.md`.
**Decision:** Accepted option 3 — Published Language, not a Shared Kernel.
**Rationale:** See `docs/decisions/0004-fleetflow-contracts-published-language.md` for
full reasoning.

---

## 2026-09-08 — LLM model selection for FR8/FR16 (Steven)
**Prompt/context:** Closing the open LLM model decision from `requirements-outline.md`
§12.1, which was blocking FR8 and FR16 heading into week 7.
**Proposed by agent:** Three options (Gemini free tier, Groq free tier, self-hosted
Ollama) evaluated against the $0 budget constraint and FR8's locked 5-second dispatch
timeout, written up in `docs/decisions/0005-llm-model-selection.md`.
**Decision:** Accepted option 1 — Gemini (`gemini-2.0-flash`) via the free Google AI
Studio API.
**Rationale:** See `docs/decisions/0005-llm-model-selection.md` for full reasoning.

---