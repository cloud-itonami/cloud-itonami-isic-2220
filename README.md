# cloud-itonami-isic-2220

Open Business Blueprint for **ISIC Rev.5 2220**: manufacture of
plastics products -- injection-molding-run-batch intake, per-product-
class material-spec evidence verification, end-of-line short-shot/
flash/warpage quality screening, robot injection-mold clamping-force
verification and Material Certificate of Compliance finalization for a
community injection-molding plant.

This repository publishes an injection-molding-plant manufacturing
actor -- molding-run-batch intake, per-product-class material-spec
evidence-checklist verification, end-of-line defect screening, robot
clamp-force verification mission and Material Certificate of
Compliance issuance -- as an OSS business that any qualified
injection-molding plant can fork, deploy, run, improve and sell, so a
plant keeps its own production and material-conformance history
instead of renting a closed MES / quality SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **Molding Advisor ⊣
Clamp-Force Governor**.

## Scope note: the missing shared upstream stage for BOTH smartphones and vehicles

This repository is scoped to **manufacturing injection-molded plastics
products** (mold clamping-force verification, per-product-class
material-spec evidence, end-of-line defect screening, molding-run-
batch shipment and Material Certificate of Compliance issuance). It is
not a device-assembly or vehicle-assembly vertical itself. Injection-
molded plastics manufacturing sits directly UPSTREAM of BOTH chains
this session has been building out:

- `cloud-itonami-isic-2630` -- manufacture of communication equipment
  (smartphone/communication-device assembly). Smartphone back-covers,
  internal chassis brackets and other housing components are
  fundamentally injection-molded-plastics outputs; `commsdevice.
  robotics`'s own display-bonding-press mission runs downstream of
  THIS actor's `:actuation/ship-molding-run-batch` hand-off for the
  consumer-electronics-housing product class.
- `cloud-itonami-isic-2910` -- manufacture of motor vehicles (final
  assembly) / `cloud-itonami-isic-2920` -- manufacture of bodies
  (coachwork) for motor vehicles. Bumper fascias, interior trim and
  dashboard components are likewise fundamentally injection-molded-
  plastics outputs -- `automotive.governor`'s/`bodyshop.governor`'s
  own end-of-line quality gates assume a finished, certified plastics
  component already exists as an input.

This vertical is the natural UNIFYING upstream stage for both chains:
neither a smartphone housing nor an automotive bumper/interior-trim
part can ship without a material-spec-verified, clamp-force-verified
molding-run-batch first passing THIS actor's gates. Distinct from:

- `cloud-itonami-isic-2630` -- device ASSEMBLY (consumes molding-run-
  batches for housings/brackets, does not produce them).
- `cloud-itonami-isic-2910`/`cloud-itonami-isic-2920` -- vehicle/body
  ASSEMBLY (consumes molding-run-batches for bumper/trim/dashboard
  components, does not produce them).
- `cloud-itonami-isic-2310` -- glass and glass-products manufacturing
  (an adjacent, but chemically and physically distinct, materials
  vertical -- glass panels are not plastics, and this actor's own
  clamp-force-verification physics has no analog to `glassworks.
  robotics`'s flexural-bend-test).
- `cloud-itonami-isic-2720` -- battery/accumulator manufacturing (an
  unrelated raw-material/cell-chemistry vertical).

## Upstream -> downstream hand-off (2220 -> 2630 / 2910 / 2920)

```text
cloud-itonami-isic-2220 (THIS repo: molding-run-batch clamp-force verification + material-spec cert -> released batch)
  --> cloud-itonami-isic-2630 (smartphone/communication-device assembly: back-cover/chassis-bracket housing integration)
  --> cloud-itonami-isic-2910 / cloud-itonami-isic-2920 (motor-vehicle/body assembly: bumper fascia/interior trim/dashboard integration)
```

`:actuation/ship-molding-run-batch` is the REAL hand-off event: an
injection-molding plant dispatches a clamp-force-verified, material-
spec-certified molding-run-batch onward to a downstream consumer. This
actor does not assume which downstream consumer a given batch ships to
-- the same released batch record and Material Certificate of
Compliance serve either hand-off.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (clamp-force-
verification-cell handling, end-of-line optical/dimensional scan)
operate under an actor that proposes actions and an independent
**Clamp-Force Governor** that gates them. The governor never issues a
Material Certificate of Compliance itself; `:high`/`:safety-critical`
actions (`:actuation/ship-molding-run-batch`, `:actuation/issue-
material-certificate`) require human sign-off.

**Robot process simulation is a REAL, time-stepped physics
simulation, not a symbolic field comparison** (native from day one,
per ADR-2607151600/ADR-2607152000's fleet pattern -- this vertical is a
NEW actor built to that standard, not a retrofit): `moldworks.robotics`
walks every molding-run-batch through a robot-executed injection-mold
clamping-force verification mission (`kotoba.robotics` mission/action/
telemetry-proof contracts) -- a real, tested rigid-body physics engine
(`kotoba-lang/physics-2d`) time-steps a moving mold-half rigid body
closing at a controlled velocity onto a static mold-half rigid body,
and reads a real peak clamp force/tonnage
(`:sim-peak-clamp-force-n`/`:sim-peak-clamp-tonnage`, Newtons/US
ton-force) directly off the simulated collision -- not an invented or
hand-set number. The Clamp-Force Governor independently re-derives the
batch's own `:sim-peak-clamp-tonnage` against the batch's own
`required-clamp-tonnage-tons` -- derived from the part's own projected
area and material class via the REAL, widely-cited plastics-processing
engineering heuristic (clamp force (tons) ~= projected part area
(in^2) x a material-specific cavity-pressure factor, ~2-5 tons/in^2 for
easy-flow materials like polypropylene/ABS and ~6-8 tons/in^2 for
harder/glass-filled/engineering-grade materials like glass-filled
nylon or PC/ABS blends common in automotive) -- never trusting the
mission's self-reported verdict alone (see `moldworks.robotics`'s own
docstring for the full honest disclosure of every engineering prior
this simulation uses, including the confidence disclosure that this
cavity-pressure-factor heuristic is a widely-cited engineering rule of
thumb, not a single formal standard).

## Core contract

```text
molding-run-batch intake + material-spec-rules verify + end-of-line quality screen
  -> Molding Advisor proposal
  -> Clamp-Force Governor (HARD holds un-overridable)
  -> phase gate (actuation always escalates)
  -> human approval for high stakes
  -> append-only ledger + draft records
```

## Actuation honesty

Shipping a molding-run-batch onward to a downstream consumer via a
robot handling/dispatch action and issuing a Material Certificate of
Compliance produce **unsigned draft records and ledger facts only**.
This actor does not talk to real plant control systems or a downstream
consumer's own intake portal. Signature and hardware dispatch are the
injection-molding plant's own acts.

## Ops

| Op | Effect |
|---|---|
| `:molding-run-batch/intake` | normalize molding-run-batch directory patch (phase 3 may auto-commit when clean) |
| `:material-spec-rules/verify` | per-product-class material-spec evidence checklist (SAE/ASTM/ISO automotive / UL/ISO consumer-electronics-housing; always human) |
| `:end-of-line-quality/screen` | end-of-line short-shot/flash/warpage defect screen (HARD hold if unresolved) |
| `:robotics/simulate-clamp-force-test` | robot injection-mold clamping-force verification mission (always human; required on file before shipment) |
| `:actuation/ship-molding-run-batch` | draft molding-run-batch-shipment record onward to a downstream consumer (always human; HARD hold if robotics-sim missing, independently under-clamped, or shrinkage-rate deviation out of range) |
| `:actuation/issue-material-certificate` | draft Material Certificate of Compliance record (always human) |

## Material-spec schemes (honest coverage)

`moldworks.facts` seeds two REAL, current, cited product-class schemes
-- see that namespace's own docstring for the full honest disclosure
of why these keys are not a simple per-country code table (injection-
molded-plastics material conformance is organized around named
engineering-standards bodies, not a per-country statute table like
`automotive.facts`'s vehicle type-approval):

- **AUTOMOTIVE** -- automotive interior/exterior plastics: SAE J1545/
  J1885 (color/appearance-durability), ASTM D638 (tensile properties),
  ASTM D256 (Izod impact resistance), ISO 3167 (multipurpose test
  specimens).
- **CE-HOUSING** -- consumer-electronics enclosure plastics: UL 94
  (mandatory flammability rating for electronic-device enclosures),
  ISO 294 (injection molding of thermoplastic test specimens).

A product class not in this table (e.g. the demo's `"MEDDEV"`
medical-device-housing scheme) has NO spec-basis and the Clamp-Force
Governor HARD-holds rather than inventing one -- see `moldworks.facts`
for the full coverage discipline.

## Social / regulatory hand-off

```clojure
(require '[moldworks.store :as store]
         '[moldworks.export :as export])

(def db (store/seed-db))
(export/audit-package db)           ;; EDN maps for downstream-consumer/quality-audit hand-off
(export/package->csv-bundle db)     ;; CSV bundle (molding-run-batches/ledger/shipments/material-certificates)
```

Operator console (static sample): `docs/samples/operator-console.html`.

## Develop

```bash
clojure -M:dev:test
clojure -M:lint
clojure -M:dev:run
```

## License

AGPL-3.0-or-later — see `LICENSE`.

## Operator console (Pages)

After enabling GitHub Pages (Settings → Pages → GitHub Actions), the
static console is at:

https://cloud-itonami.github.io/cloud-itonami-isic-2220/

Local: open `docs/index.html` or `docs/samples/operator-console.html`.

## Export audit package (CLI)

```bash
clojure -M:dev:export
# or: clojure -M:dev:export /tmp/audit-2220
```

Writes CSV files under `out/audit-package/` (or the given directory).
