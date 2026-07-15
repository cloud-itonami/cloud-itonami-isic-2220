# ADR-0001: Molding Advisor ⊣ Clamp-Force Governor architecture

- Status: Accepted (2026-07-15)
- Repository: `cloud-itonami-isic-2220` (ISIC Rev.5 `2220`)

## Context

Injection-molded-plastics manufacturing (mold clamping-force
verification, per-product-class material-spec evidence verification,
end-of-line short-shot/flash/warpage inspection, Material Certificate
of Compliance issuance) needs the same governed-actor pattern as the
rest of the cloud-itonami fleet: an untrusted advisor proposes; an
independent governor may HOLD; high-stakes actuation never
auto-commits.

The industry-registry entry for `2220` had sat at `:maturity :spec`
placeholder (`gftdcojp/cloud-itonami-C2220`) with no repo, no business
model, no actor. A 2026-07-15 value-chain review found `cloud-itonami-
isic-2630` (communication-equipment/smartphone assembly) and
`cloud-itonami-isic-2910`/`cloud-itonami-isic-2920` (motor-vehicle/body
assembly) all implemented, with shared upstream materials stages
already built for both -- batteries (`cloud-itonami-isic-2720`) and
glass (`cloud-itonami-isic-2310`) -- but injection-molded PLASTICS, a
manufacturing stage feeding BOTH chains just as directly (smartphone
housings/back-covers/internal chassis brackets AND vehicle bumpers/
interior trim/dashboard components are all fundamentally plastics-
manufacturing outputs), had no actor at all.

This vertical additionally adopts ADR-2607151600/ADR-2607152000's
real-engineering-simulation fleet pattern NATIVELY from day one --
mirroring how `cloud-itonami-isic-2720`/`cloud-itonami-isic-2310` were
each built real-physics-first.

## Decision

1. Namespaces live under `moldworks.*` with the standard facts /
   registry / store / governor / phase / advisor / operation / sim /
   robotics / export shape.
2. Entity is a **molding-run-batch** (a manufactured lot of
   injection-molded parts from one mold+material combination), not a
   finished device, a finished vehicle, or a raw material.
3. Dual actuation on the same entity:
   - `:actuation/ship-molding-run-batch` (robot molding-run-batch-
     shipment dispatch draft, onward to a downstream consumer -- the
     real dual hand-off to BOTH `cloud-itonami-isic-2630`'s
     consumer-electronics housing integration and `cloud-itonami-
     isic-2910`/`cloud-itonami-isic-2920`'s automotive plastics
     integration)
   - `:actuation/issue-material-certificate` (Material Certificate of
     Compliance draft)
4. Double-actuation guards use dedicated booleans
   (`:molding-run-batch-shipped?`, `:material-certified?`), never a
   status lifecycle (ADR-2607071320 / 6492 lesson).
5. `molding-run-batch-shrinkage-out-of-range?` continues the fleet
   two-sided range check family, applied here to a batch's own
   measured shrinkage-rate deviation from its own molded material's
   rated shrinkage factor -- a real post-mold dimensional-QA metric,
   distinct from the physics-derived clamp-force check.
6. `moldworks.robotics` delivers a REAL, time-stepped `physics-2d`
   rigid-body injection-mold clamping-force verification simulation
   from day one (not a symbolic field comparison, and not a retrofit):
   a moving mold-half `Body2D` closes at a controlled velocity onto a
   static mold-half `Body2D`; `:sim-peak-clamp-force-n`/`:sim-peak-
   clamp-tonnage` are read directly off the actual simulated collision
   trajectory. The governor HARD-holds if the mission never ran, OR if
   an independent recompute of the batch's own `:sim-peak-clamp-
   tonnage` falls below the batch's own `required-clamp-tonnage-tons`
   (derived from the part's own projected area and material's own
   cavity-pressure factor via a REAL, widely-cited plastics-processing
   engineering heuristic -- disclosed HONESTLY as a reasoned industry
   rule of thumb, not a single formal-standard numeric threshold,
   UNLIKE `cellworks.robotics`'s UN 38.3 T6 13 kN ceiling which IS a
   single formal standard's own cited number) -- never trusting the
   mission's self-reported verdict.
7. Material-spec scheme catalog (`moldworks.facts`) seeds AUTOMOTIVE
   (SAE J1545/J1885 + ASTM D638/D256 + ISO 3167) and CE-HOUSING (UL 94
   + ISO 294) only; missing product classes (e.g. medical-device
   housings) are uncovered, never fabricated.
8. End-of-line defect (short-shot/flash/warpage) unresolved is
   evaluated unconditionally so `:end-of-line-quality/screen` itself
   can HARD-hold (parksafety ADR-2607071922 Decision 5 discipline,
   same as `automotive.governor`'s/`cellworks.governor`'s/
   `glassworks.governor`'s end-of-line-defect-unresolved checks).
9. `moldworks.robotics`'s clamp-tonnage tolerance check is
   DELIBERATELY ONE-SIDED (only under-clamping flags a HARD violation)
   -- insufficient clamp force is the real defect-risk direction
   (mold-half separation under injection pressure -> flash; starved
   holding pressure in remote cavities -> short-shot), unlike
   `glassworks.robotics`'s two-sided flexural-strength acceptance
   band -- a disclosed, deliberate asymmetry matching the real
   physics, not an oversight.

## Consequences

(+) The injection-molded-plastics manufacturing stage gains a forkable
OSS operating stack with auditable governor holds, closing a gap
common to BOTH the smartphone-assembly and vehicle-assembly value
chains the 2026-07-15 value-chain review identified -- the SAME
dual-downstream-hand-off shape `cloud-itonami-isic-2720`/
`cloud-itonami-isic-2310` established for batteries and glass.
(+) Delivers a REAL time-stepped physics simulation (not a symbolic
comparison) as a native part of this actor's initial build, extending
ADR-2607151600/ADR-2607152000's fleet pattern to a NEW actor rather
than retrofitting an existing symbolic one -- and anchors its
tolerance ceiling on a REAL, widely-cited plastics-processing
engineering heuristic (projected-area x cavity-pressure factor),
honestly disclosed as a moderate-confidence industry rule of thumb
rather than a single formal-standard number.
(+) Genuine dual-downstream hand-off value: the same molding-run-
batch-shipment/material-certificate shape serves both
`cloud-itonami-isic-2630` and `cloud-itonami-isic-2910`/
`cloud-itonami-isic-2920` without this actor needing to know which
downstream consumer a given shipment goes to.
(-) No physical plant digital-twin tick beyond the single clamp-force
physics check in this repo (follow-up domain data, e.g. molten-
plastic fill/pack-hold rheology simulation, is out of scope here --
`physics-2d` has no rheology/thermal model at all).
(-) Material-spec-scheme coverage is a starting catalog (2 product
classes), not exhaustive, and does not capture every product class an
injection-molding plant might produce (e.g. medical-device housings,
food-contact packaging).
(-) `physics-2d` is a 2D projection with no material-stiffness/
deformation model, and BOTH mold-halves are approximated as flat-plate
AABBs (a disclosed simplification necessitated by `physics-2d`'s
narrowphase) -- see `moldworks.robotics`'s own docstring for the full
disclosure, including why this simulation's `mold-approach-travel-m`
has NO literal-standard or measured-material anchor at all (unlike
`cellworks.robotics`'s UN 38.3 T6 50%-deformation citation or
`glassworks.robotics`'s measured brittle-fracture-deflection
estimate) -- a rigid mold-clamp stop genuinely has no analogous
deformation distance.

## Related

- ADR-2607011000 (robotics premise + ISIC coverage)
- ADR-2607111600 (isic-2910 motor-vehicle promotion -- sibling
  architecture this repo mirrors)
- ADR-2607151600 (real engineering-simulation integration, automotive
  pilot)
- ADR-2607152000 (real engineering-simulation fleet extension)
- Superproject fleet ADR for this promotion: `90-docs/adr/2607160700-
  cloud-itonami-isic-2220-plastics.md`
- Sibling architecture: `cloud-itonami-isic-2720` docs/adr/0001,
  `cloud-itonami-isic-2310` docs/adr/0001
