(ns moldworks.governor
  "Clamp-Force Governor -- the independent compliance layer that earns
  the Molding Advisor the right to commit. The LLM has no notion of
  material-spec evidence law, whether a molding-run-batch's own
  measured shrinkage-rate deviation actually stays within its own
  recorded acceptance-band bounds, whether an end-of-line-detected
  defect against the batch has actually stayed unresolved, or when an
  act stops being a draft and becomes a real-world robot batch
  shipment or Material Certificate of Compliance issuance, so this
  MUST be a separate system able to *reject* a proposal and fall back
  to HOLD -- the injection-molding-plant analog of
  `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Seven checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated material-spec basis, incomplete evidence, a robot
  clamp-force verification simulation that never ran or that
  independently re-checks under-clamped, an out-of-spec shrinkage-rate
  deviation, an unresolved end-of-line defect, or a double shipment/
  certificate-issuance). The confidence/actuation gate is SOFT: it asks
  a human to look (low confidence / actuation), and the human may
  approve -- but see `moldworks.phase`: for `:stake :actuation/ship-
  molding-run-batch`/`:actuation/issue-material-certificate` (a real
  safety-critical act) NO phase ever allows auto-commit either. Two
  independent layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the material-spec-rules
                                       evidence proposal cite an
                                       OFFICIAL source
                                       (`moldworks.facts`), or invent
                                       one?
    2. Evidence incomplete         -- for `:actuation/ship-molding-
                                       run-batch`/`:actuation/issue-
                                       material-certificate`, has the
                                       batch actually been verified
                                       with a full material-spec
                                       evidence checklist (ISO/ASTM/
                                       SAE/UL test reports etc.) on
                                       file?
    3. Robot simulation missing or
       independently under-clamped  -- for `:actuation/ship-molding-
                                       run-batch`, has the robot
                                       clamp-force verification mission
                                       (`moldworks.robotics`) actually
                                       run and been recorded on the
                                       batch (`:robotics-sim-
                                       verified?`)? AND INDEPENDENTLY
                                       recompute whether the batch's
                                       own recorded REAL `physics-2d`-
                                       simulated clamp-tonnage
                                       telemetry (`:sim-peak-clamp-
                                       tonnage`, from ADR-2607151600/
                                       ADR-2607152000's real
                                       time-stepped simulation) falls
                                       below the batch's own recorded
                                       `required-clamp-tonnage-tons`
                                       (`moldworks.robotics/
                                       simulation-out-of-tolerance?`),
                                       ignoring whatever :passed?
                                       verdict the mission run itself
                                       stored -- the same 'ground
                                       truth, not self-report'
                                       discipline check 4 below uses
                                       for shrinkage-rate.
    4. Molding-run-batch shrinkage
       out of range                  -- for `:actuation/ship-molding-
                                       run-batch`, INDEPENDENTLY
                                       recompute whether the batch's
                                       own measured shrinkage-rate
                                       deviation falls outside its own
                                       recorded acceptance-band bounds
                                       (`moldworks.registry/molding-
                                       run-batch-shrinkage-out-of-
                                       range?`) -- needs no proposal
                                       inspection or stored-verdict
                                       lookup at all. A further
                                       instance of this fleet's
                                       two-sided range check family
                                       (see `moldworks.registry`'s ns
                                       docstring for the lineage).
    5. End-of-line defect
       unresolved                    -- reported by THIS proposal
                                       itself (an `:end-of-line-
                                       quality/screen` that just found
                                       an unresolved defect), or
                                       already on file for the batch
                                       (`:end-of-line-quality/screen`/
                                       `:actuation/issue-material-
                                       certificate`). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME
                                       discipline `casualty.governor/
                                       sanctions-violations`/
                                       `automotive.governor`/
                                       `cellworks.governor`/
                                       `glassworks.governor` (prior
                                       siblings) established --
                                       exercised in tests/demo via
                                       `:end-of-line-quality/screen`
                                       DIRECTLY, not via an actuation
                                       op against an unscreened batch
                                       -- see this ns's own test suite.
    6. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/ship-
                                       molding-run-batch`/`:actuation/
                                       issue-material-certificate`
                                       (REAL safety-critical acts) ->
                                       escalate.

  Two more guards, double-shipment/double-certificate-issuance
  prevention, are enforced but NOT listed as numbered HARD checks
  above because they need no upstream comparison at all --
  `already-shipped-violations`/`already-certified-violations` refuse
  to ship a molding-run-batch action/issue a Material Certificate of
  Compliance for the SAME batch twice, off dedicated `:molding-run-
  batch-shipped?`/`:material-certified?` facts (never a `:status`
  value) -- the SAME 'check a dedicated boolean, not status'
  discipline every prior sibling governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320)."
  (:require [moldworks.facts :as facts]
            [moldworks.registry :as registry]
            [moldworks.robotics :as robotics]
            [moldworks.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Shipping a real molding-run-batch onward to a downstream consumer
  and issuing a real Material Certificate of Compliance are the two
  real-world actuation events this actor performs -- a two-member set,
  matching every prior dual-actuation sibling's shape."
  #{:actuation/ship-molding-run-batch :actuation/issue-material-certificate})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:material-spec-rules/verify` (or actuation) proposal with no
  spec-basis citation is a HARD violation -- never invent a product
  class's material-spec requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:material-spec-rules/verify :actuation/ship-molding-run-batch :actuation/issue-material-certificate} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は材料規格要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/ship-molding-run-batch`/`:actuation/issue-material-
  certificate`, the product class's required material-spec evidence
  (ISO/ASTM/SAE/UL test reports etc.) must actually be satisfied -- do
  not trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/ship-molding-run-batch :actuation/issue-material-certificate} op)
    (let [a (store/molding-run-batch st subject)
          verification (store/material-spec-verification-of st subject)]
      (when-not (and verification
                     (facts/required-evidence-satisfied?
                      (:jurisdiction a) (:checklist verification)))
        [{:rule :evidence-incomplete
          :detail "製品区分の必要材料規格書類(ISO/ASTM/SAE/UL試験報告書等)が充足していない状態での提案"}]))))

(defn- robotics-simulation-violations
  "For `:actuation/ship-molding-run-batch`: HARD hold if the robot
  clamp-force verification mission (`moldworks.robotics`) never ran
  and was recorded on the batch (`:robotics-sim-verified?`), OR if it
  did but an INDEPENDENT recompute of the batch's own REAL `physics-
  2d`-simulated clamp-tonnage telemetry (`:sim-peak-clamp-tonnage`,
  ADR-2607151600/ADR-2607152000 -- `moldworks.robotics/simulation-out-
  of-tolerance?`) falls below the batch's own required clamp tonnage
  right now -- never trusts the mission's own stored :passed? verdict
  alone, the same discipline `molding-run-batch-shrinkage-out-of-range-
  violations` below uses for shrinkage-rate."
  [{:keys [op subject]} st]
  (when (= op :actuation/ship-molding-run-batch)
    (let [a (store/molding-run-batch st subject)]
      (cond
        (not (:robotics-sim-verified? a))
        [{:rule :robotics-simulation-missing
          :detail (str subject " のクランプ力検証ロボットミッションが未実行・未合格")}]

        (robotics/simulation-out-of-tolerance? a)
        [{:rule :robotics-simulation-out-of-tolerance
          :detail (str subject " の実測クランプトン数(" (:sim-peak-clamp-tonnage a)
                       "トン)が独立再検証で所要トン数(" (robotics/required-clamp-tonnage-tons a)
                       "トン)を下回っている(型締不足)")}]))))

(defn- molding-run-batch-shrinkage-out-of-range-violations
  "For `:actuation/ship-molding-run-batch`, INDEPENDENTLY recompute
  whether the batch's own shrinkage-rate deviation falls outside its
  own recorded acceptance-band bounds via `moldworks.registry/molding-
  run-batch-shrinkage-out-of-range?` -- needs no proposal inspection or
  stored-verdict lookup at all, since its inputs are permanent
  ground-truth fields already on the batch."
  [{:keys [op subject]} st]
  (when (= op :actuation/ship-molding-run-batch)
    (let [a (store/molding-run-batch st subject)]
      (when (registry/molding-run-batch-shrinkage-out-of-range? a)
        [{:rule :molding-run-batch-shrinkage-out-of-range
          :detail (str subject " の実測収縮率偏差(" (:shrinkage-rate-deviation-actual-pct a)
                      "%)が許容範囲[" (:shrinkage-rate-deviation-min-pct a) ","
                      (:shrinkage-rate-deviation-max-pct a) "]%を逸脱")}]))))

(defn- end-of-line-defect-unresolved-violations
  "An unresolved end-of-line-detected defect (short-shot/flash/
  warpage) -- reported by THIS proposal (e.g. an `:end-of-line-
  quality/screen` that itself just found one), or already on file in
  the store for the batch (`:end-of-line-quality/screen`/`:actuation/
  issue-material-certificate`) -- is a HARD, un-overridable hold.
  Evaluated UNCONDITIONALLY (not scoped to a specific op) so the
  screening op itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        batch-id (when (contains? #{:end-of-line-quality/screen :actuation/issue-material-certificate} op) subject)
        hit-on-file? (and batch-id (= :unresolved (:verdict (store/eol-screen-of st batch-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :end-of-line-defect-unresolved
        :detail "未解決の完成検査欠陥(ショートショット/バリ/反り)がある状態での材料証明書発行提案は進められない"}])))

(defn- already-shipped-violations
  "For `:actuation/ship-molding-run-batch`, refuses to ship a batch
  action for the SAME batch twice, off a dedicated `:molding-run-
  batch-shipped?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/ship-molding-run-batch)
    (when (store/batch-already-shipped? st subject)
      [{:rule :already-shipped
        :detail (str subject " は既に出荷実行済み")}])))

(defn- already-certified-violations
  "For `:actuation/issue-material-certificate`, refuses to issue a
  Material Certificate of Compliance for the SAME batch twice, off a
  dedicated `:material-certified?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/issue-material-certificate)
    (when (store/batch-already-certified? st subject)
      [{:rule :already-certified
        :detail (str subject " は既に材料証明書発行済み")}])))

(defn check
  "Censors a Molding Advisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (robotics-simulation-violations request st)
                           (molding-run-batch-shrinkage-out-of-range-violations request st)
                           (end-of-line-defect-unresolved-violations request proposal st)
                           (already-shipped-violations request st)
                           (already-certified-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
