# cloud-itonami-isic-2220: Manufacture of plastics products

Open Business Blueprint for **ISIC Rev.5 2220**: manufacture of plastics products — an autonomous "actor" (LLM advisor behind an independent Governor, langgraph-clj StateGraph, append-only audit ledger) that coordinates back-office plastics-products plant **operations**: production-batch data logging (resin-type/weight/reject-rate), injection-molding/extrusion/blow-molding-equipment maintenance scheduling, safety-concern flagging, and outbound product shipment coordination.

This repository designs a forkable OSS business for plastics-products
plant operations: run by a qualified operator so a plant keeps its own
operating records instead of renting a closed SaaS.

## What this actor does

Proposes **plant operations coordination**, not equipment operation:
- `:log-production-batch` — resin-type/weight/reject-rate data logging (administrative, not an operational decision)
- `:schedule-maintenance` — injection-molding/extrusion/blow-molding-equipment maintenance scheduling proposal
- `:flag-safety-concern` — surface a materials-safety/fume-hazard/equipment-safety concern (always escalates)
- `:coordinate-shipment` — outbound plastics-product shipment coordination proposal

## What this actor does NOT do

**CRITICAL SCOPE BOUNDARY — this is a safety-critical domain**
(injection-molding/extrusion/blow-molding equipment, resin fume/VOC
hazard, thermal/pressure hazard):

- Does NOT control injection-molding, extrusion, or blow-molding line equipment directly
- Does NOT make plant-safety or materials-safety decisions (that's the plant supervisor's exclusive human authority)
- Does NOT actuate the molding/extrusion line (human plant supervisor decides)
- ONLY proposes/coordinates operations back-office; all actuation requires explicit human approval
- Safety-concern flagging ALWAYS escalates — never auto-decided, no confidence threshold or phase below escalation

## Architecture

Classic governed-actor pattern (`plasticsmfg.operation/build`, a langgraph-clj StateGraph):
1. **`plasticsmfg.advisor`** (sealed intelligence node, `PlasticsAdvisor`): proposes decisions only, never commits
2. **`plasticsmfg.governor`** (independent, `Plastics Plant Operations Governor`): validates against domain rules, re-derived from `plasticsmfg.registry`'s pure functions and `plasticsmfg.store`'s SSoT -- never trusts the advisor's own self-report
   - HARD invariants (always `:hold`, no override):
     - Plant/batch record must be independently verified/registered (`:verified?` AND `:registered?`) before any action is taken against it (equipment before maintenance scheduling, batch before shipment coordination)
     - The request's own `:effect` must be `:propose` (never a direct-write bypass)
     - `:op` must be in the closed four-op allowlist
     - The proposal's own `:effect` must be one of the four propose-shaped effects (no direct molding/extrusion/blow-molding-line-equipment control)
     - Directly actuating the molding/extrusion line (`:actuate-line? true`) is a PERMANENT, unconditional block
     - A shipment may not push a batch's own recorded shipped weight past its own logged production weight (independently recomputed)
     - No double-scheduling the same maintenance record
     - No fabricated `:resin-type` value on a production-batch patch
     - No physically implausible `:reject-rate-percent` value on a production-batch patch
   - ESCALATE (always human sign-off, overridable by a human):
     - `:flag-safety-concern` always escalates, regardless of confidence
     - Low-confidence proposals
3. **`plasticsmfg.phase`** (Phase 0->3 rollout): `:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment` are NEVER in any phase's `:auto` set (permanent, matching the governor's own posture); only `:log-production-batch` may auto-commit at phase 3 when clean
4. **`plasticsmfg.store`** (append-only audit ledger + SSoT): a single `MemStore` backend behind a `Store` protocol (see ns docstring for why a second Datomic-backed backend is out of scope for this build)

## Development

```bash
# Run tests (top-level deps.edn already pins langgraph+langchain local/root)
clojure -M:test

# Run tests via the workspace :dev override alias (equivalent, kept for sibling-repo parity)
clojure -M:dev:test

# Run the demo
clojure -M:dev:run

# Lint
clojure -M:lint
```

## Status

`:implemented` — `governor.cljc`/`store.cljc`/`advisor.cljc`/`registry.cljc` + `deps.edn` complete the module set; tests green, demo runnable, langgraph-clj integration verified.

## License

AGPL-3.0-or-later
