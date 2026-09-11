(ns moldworks.robotics
  "Robot-executed injection-mold CLAMPING-FORCE verification -- the
  concrete, actor-level realization of ADR-2607011000's robotics
  premise (every cloud-itonami vertical is designed on the premise that
  a robot performs the physical-domain work; an independent governor
  gates any action before it ever reaches hardware), delivered NATIVELY
  onto ADR-2607151600/ADR-2607152000's real-engineering-simulation
  fleet pattern from day one (this vertical, isic-2220, is a NEW actor
  built to that same standard from day one, mirroring how
  `cloud-itonami-isic-2720`/`cloud-itonami-isic-2310` deliver it
  natively rather than retrofitted; reference implementations:
  `cellworks.robotics`'s UN 38.3 T6 crush-test simulation,
  `glassworks.robotics`'s ASTM C158 flexural-bend-test simulation) for
  THIS actor's own manufacturing-process evidence requirement: a
  molding-run-batch-shipment proposal must cite a real clamp-force
  verification report actually on file -- not merely a self-reported
  checklist string.

  The clamp-force-verification step of the mission is an ACTUAL
  time-stepped `kotoba-lang/physics-2d` rigid-body simulation of a
  REAL, standard injection-molding QA/process-control step: a moving
  mold-half `Body2D` (the clamp unit's moving platen, carrying one
  mold-half) closes at a controlled velocity onto a static (mass 0,
  immovable -- the SAME `cellworks.robotics`/`glassworks.robotics`/
  `bodyshop.robotics` fixture/anvil pattern) mold-half `Body2D` (bolted
  to the clamp unit's stationary platen). `world-step` actually
  integrates/collides/resolves the contact over real ticks, and
  `:sim-peak-clamp-force-n`/`:sim-peak-clamp-tonnage` are read directly
  off the ACTUAL simulated velocity trajectory (F = m*a, the SAME
  technique every real-physics sibling in this fleet uses) -- not
  invented.

  A robot mission (`kotoba.robotics/mission`) walks the molding-run-
  batch through three steps in the clamp-force-verification cell -- a
  pre-run mold-alignment/tonnage-setpoint check, the clamp-force
  verification cycle itself, and a post-run part dimensional/flash
  scan -- built with `kotoba.robotics/action` + `kotoba.robotics/
  telemetry-proof`, and reports an overall :passed? verdict now derived
  from the REAL simulated clamp reading (`:sim-peak-clamp-tonnage`, see
  `clamp-telemetry-for`), not a hand-set field. `simulation-out-of-
  tolerance?` independently re-derives that verdict from the batch's
  OWN recorded real telemetry cross-checked against the batch's OWN
  recorded `required-clamp-tonnage-tons` (derived from the part's own
  projected area and its material's own cavity-pressure factor -- see
  below), never from the mission's self-reported result -- the SAME
  'ground truth, not self-report' discipline
  `moldworks.registry/molding-run-batch-shrinkage-out-of-range?` uses
  for shrinkage-rate deviation. `moldworks.governor`'s
  `robotics-simulation-violations` calls this ns's independent recheck,
  never the stored :passed? value, before any `:actuation/ship-molding-
  run-batch` proposal may commit.

  Honest scope + citation disclosure (mirrors every real-physics
  sibling's own disclosure style, ADR-2607151600/ADR-2607152000):

  - 2D projection only (`physics-2d` has no 3D solver) -- x is the
    clamp unit's direction of travel (mold-open/mold-close axis); world
    gravity is [0 0] (a horizontal clamp-closing projection, matching
    a real horizontal injection-molding machine's clamp axis -- the
    dominant machine architecture for the mid/large tonnage classes
    this ns's own worked examples model).
  - the static mold-half is modeled as a STATIC (mass 0) AABB,
    mirroring `cellworks.robotics`'s cylindrical-cell / `glassworks.
    robotics`'s glass-panel-specimen pattern: `physics-2d` treats a
    mass-0 body as having zero inverse mass (an immovable anchor),
    which is also physically apt here -- the stationary platen (and
    the mold-half bolted to it) does not move during clamp-up; only
    the moving platen (and its mold-half) travels.
  - BOTH mold halves are modeled as rectangular AABBs (a flat plate on
    the travel axis, wide laterally) -- a disclosed simplification
    necessitated by `physics-2d`'s narrowphase, which only supports
    AABB-vs-AABB or circle-vs-circle pairs (`test-collision` returns
    nil for mixed pairs); a real mold-half's cavity/core detail
    geometry is far more complex than a flat plate, but the CLAMP-UP
    contact event this ns actually models is the mold-halves' own flat
    PARTING-LINE faces meeting, which a flat AABB genuinely represents
    honestly, not merely as a stand-in. `physics-2d` has NO
    material-stiffness/deformation model whatsoever, so neither
    mold-half's own real steel rigidity varies the simulated reading --
    what DOES vary the reading is this molding-run-batch's own recorded
    press-run configuration (`:clamp-unit-moving-platen-mass-kg`, see
    `clamp-telemetry-for`), the SAME disclosed lever every real-physics
    sibling in this fleet uses.
  - UNLIKE `cellworks.robotics`'s UN 38.3 T6 crush test (whose
    crush-travel distance is a LITERAL citation of the standard's own
    50%-deformation stopping criterion) or `glassworks.robotics`'s
    ASTM C158 bend test (whose specimen-deflection-at-failure is a
    disclosed engineering estimate of a REAL brittle-fracture
    deflection), a rigid injection-mold clamp-up has NO analogous
    deformation/give distance at all -- both mold halves are rigid
    steel tooling that come to a hard mechanical stop at the parting
    line, not a specimen that deforms or fractures. This ns therefore
    has NO literal-standard or measured-material distance to anchor
    `mold-approach-travel-m` on; it is a DISCLOSED ARBITRARY rigid-body
    stand-in distance for the clamp unit's final \"mold-protect\"
    approach zone immediately before parting-line contact (the same
    kind of disclosed-arbitrary distance `bodyshop.robotics`'s
    `die-half-w-m` uses for its own rigid stamping-die dimension) --
    honestly disclosed as WEAKER-anchored than either UN 38.3's literal
    citation or ASTM C158's measured-material estimate, not glossed
    over as equivalent to either.
  - `mold-closing-velocity-mps` (1.0 m/s) is a disclosed ANALOG closing
    rate for the clamp unit's final approach, NOT a literal
    transcription of any one real injection-molding machine's actual
    \"mold-protect\" creep speed (real mold-protect speeds are
    typically much slower, low mm/s to a few cm/s, to protect the
    tooling from a hard-metal-on-metal impact) -- `physics-2d`'s
    impulse resolver has NO progressive force-vs-displacement
    stiffness model at all (the SAME disclosed limitation every
    real-physics sibling states): whatever tick first detects ANY AABB
    overlap fully zeroes the closing velocity in that ONE tick
    (restitution 0) -- a discrete, instantaneous stop, not a real
    clamp unit's actual controlled deceleration ramp. This ns uses a
    faster, disclosed analog rate instead, the SAME disclosed choice
    every real-physics sibling in this fleet makes.
  - By exact kinematic identity (peak deceleration = closing-velocity /
    dt for a single-tick full stop against an immovable body, the SAME
    verified, documented property every real-physics sibling in this
    fleet establishes -- mass cancels algebraically in `physics-2d`'s
    `resolve-contact` when colliding with a mass-0 body), the peak
    deceleration itself is INDEPENDENT of `clamp-unit-moving-platen-
    mass-kg` -- so `:clamp-unit-moving-platen-mass-kg` is the ONLY
    quantity that scales `:sim-peak-clamp-force-n`/`:sim-peak-clamp-
    tonnage` for a fixed closing velocity/approach distance (via
    F = m*a), never the closing velocity or approach travel (both
    fixed constants, shared by every molding-run-batch). A heavier
    moving-platen assembly stands in for a larger-tonnage-class
    machine (real large presses' moving-platen assemblies, inclusive
    of the mounted mold-half, plausibly weigh many tonnes for the
    multi-hundred/multi-thousand-ton machine classes this ns's own
    worked examples model -- disclosed as an illustrative simulation
    parameter, not a literal per-machine-model spec transcription).
  - `us-ton-force-n` (8896.44323 N per US ton-force, i.e. 2000 lbf) is
    a PLAIN UNIT-CONVERSION CONSTANT, not an engineering estimate --
    HIGH confidence (exact, by definition: 1 lbf = 4.4482216152605 N).
    US injection-molding machines are conventionally rated in US
    ton-force (\"clamp tonnage\"); this ns uses that convention for
    `:sim-peak-clamp-tonnage` and `required-clamp-tonnage-tons`.
  - `cavity-pressure-factor-tons-per-in2` -- the REAL, well-known
    plastics-processing engineering heuristic this ns anchors its
    tolerance on: required clamp force (tons) ~= projected part area
    (in^2) x a material-specific cavity-pressure factor, commonly
    cited in plastics-processing engineering references as roughly
    2-5 tons/in^2 for easy-flow materials (e.g. polypropylene, ABS) and
    up to 6-8 tons/in^2 for harder/glass-filled/engineering-grade
    materials (glass-filled nylon, PC/ABS blends common in automotive).
    HONEST CONFIDENCE DISCLOSURE: this is a WIDELY-CITED ENGINEERING
    RULE OF THUMB used across plastics-processing/moldmaking industry
    references, NOT a single formal standards-body-issued numeric
    threshold (unlike UN 38.3 T6's cited 13 kN crush-force ceiling,
    which IS a single formal standard's own number) -- this ns
    discloses a MODERATE-confidence, REASONED ENGINEERING ESTIMATE
    representative factor per material class (`cavity-pressure-
    factor-for`: 3.5 tons/in^2 for `:easy-flow`, a mid-range point in
    the cited 2-5 band; 7.0 tons/in^2 for `:engineering-grade`, a
    mid-range point in the cited 6-8 band), the SAME confidence-
    disclosure discipline `glassworks.robotics`'s tempered/cover-glass
    flexural-strength bands use for their own reasoned-estimate
    figures.
  - `clamp-tonnage-out-of-tolerance?` is DELIBERATELY ONE-SIDED (only
    UNDER-clamping is flagged), unlike `glassworks.robotics`'s two-sided
    flexural-strength acceptance band: insufficient clamp force is the
    real defect-risk direction for injection molding -- the moving and
    static mold-halves separate microscopically under the molten
    plastic's own injection pressure at the parting line, allowing
    resin to escape as FLASH, and can starve remote/thin-wall cavity
    regions of holding pressure, contributing to SHORT-SHOT (incomplete
    fill). This ns does NOT model an upper too-much-clamp-force fault
    mode as a defect risk -- over-clamping is not a standard flash/
    short-shot failure mode; its own real risk (excess mechanical
    stress on the mold/press structure, energy waste) is a distinct
    concern this ns's honest disclosure does not claim to cover. This
    is a deliberate asymmetry matching the real physics, not an
    oversight matching `cellworks.robotics`'s own one-sided (over-only)
    crush-force ceiling check by symmetry of form, not of direction.

  Pure data + pure functions -- no real robot I/O, no network.
  `physics-2d/world-step` is itself a pure, fixed-timestep integrator
  (no wall-clock/IO), so this stays exactly as offline/deterministic as
  every other sibling namespace in this actor -- tests and the demo run
  without a network.

  Honest scope: this DOES model a real time-stepped `physics-2d` rigid-
  body trajectory for the clamp-up collision event, along the clamp
  unit's own real travel axis, and derives a real clamp-tonnage
  reading directly comparable to a real, disclosed plastics-processing
  cavity-pressure-factor heuristic. It does NOT model: molten-plastic
  rheology/fill/pack-hold dynamics, mold cavity/core 3D geometry (2D
  projection, flat-plate approximation only), a real load-cell/tie-bar-
  strain-gauge/DAQ connection, or a real clamp-unit servo-motion-
  planning/hydraulic-control system -- still simulation, not control,
  the same 'policy, not control' boundary `kotoba.robotics`'s docstring
  already establishes."
  (:require [kotoba.robotics :as robotics]
            [physics-2d :as p2d]))

;; ───────────────────── real, disclosed physical constants ─────────────────────

(def ^:const us-ton-force-n
  "US ton-force (\"short ton-force\"), the conventional US clamp-tonnage
  unit for injection-molding machines: 2000 lbf x 4.4482216152605 N/lbf.
  A PLAIN UNIT-CONVERSION CONSTANT -- HIGH (exact) confidence, distinct
  from the material/process engineering estimates below."
  8896.443230521)

(def cavity-pressure-factor-band-tons-per-in2
  "Real, widely-cited plastics-processing engineering heuristic bands
  per material class -- see ns docstring for the full honest
  confidence disclosure (a widely-cited engineering rule of thumb, not
  a single formal standard). `:easy-flow` covers materials like
  polypropylene/ABS; `:engineering-grade` covers harder/glass-filled
  materials like glass-filled nylon or PC/ABS blends common in
  automotive parts."
  {:easy-flow          {:min 2.0 :max 5.0 :confidence :reasoned-industry-heuristic}
   :engineering-grade  {:min 6.0 :max 8.0 :confidence :reasoned-industry-heuristic}})

(defn cavity-pressure-factor-for
  "This ns's own disclosed, MODERATE-confidence representative cavity-
  pressure factor (tons/in^2) for `material-class` -- a mid-range point
  in `cavity-pressure-factor-band-tons-per-in2`'s own cited band, NOT a
  per-batch measured fact. See ns docstring for the full honesty
  disclosure (a widely-cited engineering rule of thumb, not a single
  formal standard)."
  [material-class]
  (case material-class
    :easy-flow         3.5
    :engineering-grade 7.0
    nil))

(defn required-clamp-tonnage-tons
  "The REAL plastics-processing engineering heuristic this ns anchors
  its tolerance on: required clamp force (tons) = projected part area
  (in^2) x the material's own cavity-pressure factor (tons/in^2, see
  `cavity-pressure-factor-for`). Reads `batch`'s own recorded
  `:projected-part-area-in2`/`:cavity-pressure-factor-tons-per-in2`
  fields -- a pure ground-truth computation over the batch's own
  permanent fields, no simulation needed."
  [{:keys [projected-part-area-in2 cavity-pressure-factor-tons-per-in2]}]
  (when (and (number? projected-part-area-in2) (number? cavity-pressure-factor-tons-per-in2))
    (* projected-part-area-in2 cavity-pressure-factor-tons-per-in2)))

;; ------------------------- real physics-2d clamp constants -------------------------

(def ^:const mold-closing-velocity-mps
  "The clamp unit's controlled final-approach closing velocity (m/s)
  for THIS simulation -- a disclosed ANALOG rate, NOT a literal
  transcription of any one real injection-molding machine's actual
  slow \"mold-protect\" creep speed. See ns docstring for why."
  1.0)

(def ^:const mold-approach-travel-m
  "The clamp unit's nominal final-approach standoff/travel distance (m)
  before the mold-halves' own parting-line faces make contact -- a
  DISCLOSED ARBITRARY rigid-body stand-in distance (10mm), NOT a
  literal standard citation or a measured-material deformation
  distance (unlike `cellworks.robotics`'s/`glassworks.robotics`'s own
  travel constants) -- see ns docstring for the full disclosure of why
  a rigid mold-clamp stop has no such analogous distance at all."
  0.01)

(def ^:const dt
  "Per-tick timestep (s) -- derived from THIS simulation's own
  mold-approach-travel/closing-velocity (the nominal transit time
  across the clamp unit's own final-approach zone), the SAME
  principled-not-arbitrary identity every real-physics sibling uses
  for its own `dt`."
  (/ mold-approach-travel-m mold-closing-velocity-mps))

(def ^:const moving-mold-half-half-w-m
  "Moving mold-half AABB half-width (m) along the travel axis -- a
  disclosed, arbitrary rigid-body stand-in (100mm full thickness for
  the moving mold-half + platen-face assembly); `physics-2d` colliders
  do not deform, so this dimension is not a load-bearing physical
  parameter."
  0.05)

(def ^:const moving-mold-half-half-h-m
  "Moving mold-half AABB half-height (m), lateral -- 600mm full width, a
  representative mid/large-tonnage-class mold-plate lateral extent."
  0.3)

(def ^:const static-mold-half-half-w-m
  "Static mold-half AABB half-width (m) along the travel axis -- same
  disclosed rigid-body stand-in dimension as the moving mold-half."
  0.05)

(def ^:const static-mold-half-half-h-m
  "Static mold-half AABB half-height (m), lateral -- same lateral extent
  as the moving mold-half, so the whole modeled parting-line width
  interacts."
  0.3)

(def ^:const gap-m
  "Clamp-unit standoff distance (m) the moving mold-half starts behind
  the static mold-half, so the trajectory captures a real pre-contact
  approach phase, not just the collision tick itself (mirrors every
  sibling's own gap constant). Deliberately NOT an exact multiple of
  `mold-closing-velocity-mps` x `dt` (unlike a round 10mm gap would be
  here) so the simulated approach genuinely overshoots the contact
  plane by a sub-tick remainder before positional correction resolves
  it -- keeping `:sim-peak-clamp-travel-m` a genuinely observed,
  nonzero simulated reading rather than a coincidental exact-alignment
  zero."
  0.012)

(def ^:const settle-ticks
  "Extra ticks appended after the moving mold-half is expected to reach
  the static mold-half, so the trajectory also captures post-contact
  settling -- the SAME constant + rationale as every real-physics
  sibling: `physics-2d`'s positional correction removes 80% of any
  remaining overlap per tick, so residual overlap after 15 more ticks
  is ~3e-11 of whatever it was at first contact."
  15)

;; ------------------------------ real simulation ------------------------------

(defn simulate-clamp-force
  "Time-steps a REAL `physics-2d` world for ONE injection-mold
  clamping-force verification cycle: a moving mold-half `Body2D` (mass
  `clamp-unit-moving-platen-mass-kg`, velocity `mold-closing-velocity-
  mps`) approaches and collides with a static (mass 0, immovable) mold-
  half `Body2D`. Returns {:trajectory [{:tick :position :velocity} ...]
  (moving mold-half only) :sim-peak-clamp-force-n n
  :sim-peak-clamp-tonnage n :sim-peak-clamp-travel-m n :ticks n :dt n
  :closing-velocity-mps n}.

  `:sim-peak-clamp-force-n` is `clamp-unit-moving-platen-mass-kg` times
  the PEAK magnitude of tick-to-tick velocity change (along the travel
  axis) divided by `dt` -- F = m*a, derived from the ACTUAL simulated
  velocity trajectory (the SAME technique every real-physics sibling in
  this fleet uses). `:sim-peak-clamp-tonnage` converts that force into
  US ton-force via `us-ton-force-n` -- a plain, exact unit conversion.
  `:sim-peak-clamp-travel-m` is the largest AABB penetration depth (m)
  actually observed between the moving mold-half's leading face and
  the static mold-half's near face across the whole trajectory --
  informational, derived from the actual simulated positions, not
  invented.

  Pure, deterministic -- the same `clamp-unit-moving-platen-mass-kg`
  always reproduces the same telemetry; no IO, no wall-clock."
  [clamp-unit-moving-platen-mass-kg]
  (let [v0 mold-closing-velocity-mps
        approach-m (+ gap-m moving-mold-half-half-w-m static-mold-half-half-w-m)
        ticks (long (+ settle-ticks (long (Math/ceil (/ approach-m (* v0 dt))))))
        static-x 0.0
        moving-x (- static-x static-mold-half-half-w-m moving-mold-half-half-w-m gap-m)
        moving (p2d/make-body {:position [moving-x 0.0]
                                :velocity [v0 0.0]
                                :mass (double clamp-unit-moving-platen-mass-kg)
                                :restitution 0.0
                                :friction 0.0
                                :collider (p2d/make-aabb-collider moving-mold-half-half-w-m moving-mold-half-half-h-m)
                                :user-data :moving-mold-half})
        static (p2d/make-body {:position [static-x 0.0]
                                :velocity [0.0 0.0]
                                :mass 0.0
                                :restitution 0.0
                                :friction 0.0
                                :collider (p2d/make-aabb-collider static-mold-half-half-w-m static-mold-half-half-h-m)
                                :user-data :static-mold-half})
        w0 (p2d/world-new [0.0 0.0])
        [w1 moving-id] (p2d/world-add w0 moving)
        [w2 _static-id] (p2d/world-add w1 static)
        worlds (reductions (fn [w _] (p2d/world-step w dt)) w2 (range ticks))
        trajectory (mapv (fn [tick world]
                            (let [b (nth (:bodies world) moving-id)]
                              {:tick tick :position (:position b) :velocity (:velocity b)}))
                          (range (count worlds)) worlds)
        vxs (mapv (comp first :velocity) trajectory)
        peak-decel-mps2 (->> (map (fn [va vb] (Math/abs (/ (- vb va) dt))) vxs (rest vxs))
                              (reduce max 0.0))
        contact-plane-x (- static-x static-mold-half-half-w-m)
        penetrations-m (mapv (fn [{:keys [position]}]
                                (max 0.0 (- (+ (first position) moving-mold-half-half-w-m) contact-plane-x)))
                              trajectory)
        peak-force-n (* (double clamp-unit-moving-platen-mass-kg) peak-decel-mps2)]
    {:trajectory trajectory
     :sim-peak-clamp-force-n peak-force-n
     :sim-peak-clamp-tonnage (/ peak-force-n us-ton-force-n)
     :sim-peak-clamp-travel-m (reduce max 0.0 penetrations-m)
     :ticks (count trajectory)
     :dt dt
     :closing-velocity-mps v0}))

(defn clamp-telemetry-for
  "Runs the REAL `simulate-clamp-force` time-stepped `physics-2d`
  simulation for `batch`'s own recorded `:clamp-unit-moving-platen-
  mass-kg` press-run configuration and returns the actual simulated
  telemetry: {:sim-peak-clamp-force-n n :sim-peak-clamp-tonnage n
  :sim-peak-clamp-travel-m n :ticks n :dt n :closing-velocity-mps n}.
  Pure, deterministic -- the same `:clamp-unit-moving-platen-mass-kg`
  always reproduces the same telemetry."
  [batch]
  (select-keys (simulate-clamp-force (:clamp-unit-moving-platen-mass-kg batch))
               [:sim-peak-clamp-force-n :sim-peak-clamp-tonnage
                :sim-peak-clamp-travel-m :ticks :dt :closing-velocity-mps]))

(def mission-actions
  "The three-step clamp-force-verification-cell mission every molding-
  run-batch walks through before `:actuation/ship-molding-run-batch` is
  proposable. :sense at :none safety, :actuate at :low -- verification/
  QA handling of a stationary tooling set, not the moving-shipment
  actuation that is `:actuation/ship-molding-run-batch` itself (always
  :safety-critical -- see `moldworks.governor`)."
  [{:step :mold-alignment-tonnage-setpoint-check :kind :sense   :safety :none}
   {:step :clamp-force-verification-cycle         :kind :actuate :safety :low}
   {:step :post-run-part-dimensional-flash-scan    :kind :sense   :safety :none}])

(defn clamp-tonnage-out-of-tolerance?
  "Ground-truth check: does `batch`'s own recorded REAL `physics-2d`-
  simulated clamp reading (`:sim-peak-clamp-tonnage`, see
  `clamp-telemetry-for`) fall BELOW `batch`'s own recorded
  `required-clamp-tonnage-tons` (derived from the part's own projected
  area and material's own cavity-pressure factor)? DELIBERATELY
  ONE-SIDED -- see ns docstring for why only under-clamping is a
  flagged defect-risk direction here. Needs no mission run or proposal
  inspection once the telemetry and required-tonnage fields are on
  file -- its inputs are permanent fields already on the batch, the
  same shape `moldworks.registry/molding-run-batch-shrinkage-out-of-
  range?` uses for shrinkage-rate deviation."
  [{:keys [sim-peak-clamp-tonnage] :as batch}]
  (let [required (required-clamp-tonnage-tons batch)]
    (and (number? sim-peak-clamp-tonnage) (number? required)
         (< sim-peak-clamp-tonnage required))))

(defn simulate-clamp-force-test
  "Run the robot-executed clamp-force-verification mission for
  `batch-id` (`batch` is the full record, incl.
  `:clamp-unit-moving-platen-mass-kg`, `:projected-part-area-in2`,
  `:cavity-pressure-factor-tons-per-in2`). Actually runs the REAL
  engine: `clamp-telemetry-for` -- the actual `physics-2d`-stepped
  moving-mold-half/static-mold-half collision trajectory
  (`:sim-peak-clamp-force-n`/`:sim-peak-clamp-tonnage`).

  Returns {:mission .. :actions [{:action .. :proof ..} ..] :passed?
  bool :sim-peak-clamp-force-n n :sim-peak-clamp-tonnage n}.
  Deterministic: :passed? is derived from the batch's OWN recorded
  clamp-run configuration via the REAL simulated trajectory
  (`clamp-tonnage-out-of-tolerance?`), never invented or randomized --
  `kotoba.robotics` mandates no network/IO, and a repeatable simulation
  is what makes the governor's independent recheck
  (`simulation-out-of-tolerance?`) meaningful."
  [batch-id batch]
  (let [telemetry (clamp-telemetry-for batch)
        merged (merge batch telemetry)
        out-of-range? (clamp-tonnage-out-of-tolerance? merged)
        reading (if out-of-range? :out-of-tolerance :nominal)
        mission (robotics/mission (str "mission-" batch-id "-clamp-force-verify")
                                   :robot/clamp-force-cell-1
                                   :clamp-force-verification
                                   :boundaries {:station "moldworks-clamp-verification-cell"}
                                   :max-steps (count mission-actions))
        actions (mapv (fn [{:keys [step kind safety]}]
                        (let [a (robotics/action (str (:mission/id mission) "-" (name step))
                                                  (:mission/id mission) kind safety
                                                  :params {:step step :batch-id batch-id})]
                          {:action a
                           :proof (robotics/telemetry-proof (:mission/id mission) step reading
                                                             :provenance :simulated)}))
                      mission-actions)]
    {:mission mission
     :actions actions
     :passed? (not out-of-range?)
     :sim-peak-clamp-force-n (:sim-peak-clamp-force-n telemetry)
     :sim-peak-clamp-tonnage (:sim-peak-clamp-tonnage telemetry)}))

(defn simulation-out-of-tolerance?
  "Independent ground-truth recheck for the governor: does `batch`'s
  OWN current, on-file real `physics-2d`-simulated clamp-tonnage
  telemetry (`:sim-peak-clamp-tonnage`) fall below its own recorded
  `required-clamp-tonnage-tons` right now? Ignores whatever :passed?
  verdict a prior mission run stored -- identical in spirit to
  `moldworks.registry/molding-run-batch-shrinkage-out-of-range?`'s
  refusal to trust a proposal's self-report. Does NOT re-run the
  simulation -- it re-derives the boolean from the real, already-
  persisted telemetry field (`moldworks.store` persists it on every
  `:molding-run-batch/upsert`), the same 'ground truth, not self-
  report' discipline applied to the STORED reading, not a fresh
  recompute."
  [batch]
  (clamp-tonnage-out-of-tolerance? batch))
