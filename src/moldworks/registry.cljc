(ns moldworks.registry
  "Pure-function molding-run-batch-shipment + Material Certificate of
  Compliance record construction -- an append-only injection-molding-
  plant book-of-record draft.

  Like every sibling actor's registry, there is no single international
  check-digit standard for a molding-run-batch-shipment or Material
  Certificate of Compliance reference number -- every plant/scheme
  assigns its own reference format. This namespace does NOT invent one;
  it builds a jurisdiction/scheme-scoped sequence number and validates
  the record's required fields, the same honest, non-fabricating
  discipline `moldworks.facts` uses.

  `molding-run-batch-shrinkage-out-of-range?` continues this fleet's
  two-sided range check family (`testlab.registry/within-tolerance?`
  established the first; `conservation.registry`/`water.registry`/
  `steelworks.registry`/`turbine.registry`/`automotive.registry`/
  `autoparts.registry`/`bodyshop.registry`/`cellworks.registry`/
  `glassworks.registry` are further siblings), applying the SAME lo/hi
  bounds-comparison shape to a molding-run-batch's own measured
  shrinkage-rate deviation from its own molded material's rated
  shrinkage factor -- a real end-of-line dimensional-QA metric,
  distinct from `moldworks.robotics`'s own clamp-tonnage ground-truth
  check (a physics-derived process-control reading, not a post-mold
  shrinkage measurement).

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real plant/MES control system. It builds the RECORD an
  injection-molding plant would keep, not the act of shipping the
  molding-run-batch robot action or issuing the Material Certificate of
  Compliance itself (that is `moldworks.operation`'s `:actuation/ship-
  molding-run-batch`/`:actuation/issue-material-certificate`, always
  human-gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the injection-molding plant's own act, not this actor's. See README
  `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn molding-run-batch-shrinkage-out-of-range?
  "Does `batch`'s own `:shrinkage-rate-deviation-actual-pct` fall
  outside its own `[:shrinkage-rate-deviation-min-pct
  :shrinkage-rate-deviation-max-pct]` recorded acceptance-band bounds
  (a percentage-point deviation from the molded material's own rated
  shrinkage factor)? A pure ground-truth check against the batch's own
  permanent fields -- no upstream comparison needed, and no physics
  re-simulation needed (distinct from `moldworks.robotics`'s own
  clamp-tonnage ground-truth check). A further sibling in this fleet's
  two-sided range check family (see ns docstring)."
  [{:keys [shrinkage-rate-deviation-actual-pct
           shrinkage-rate-deviation-min-pct
           shrinkage-rate-deviation-max-pct]}]
  (and (number? shrinkage-rate-deviation-actual-pct)
       (number? shrinkage-rate-deviation-min-pct)
       (number? shrinkage-rate-deviation-max-pct)
       (or (< shrinkage-rate-deviation-actual-pct shrinkage-rate-deviation-min-pct)
           (> shrinkage-rate-deviation-actual-pct shrinkage-rate-deviation-max-pct))))

(defn register-molding-run-batch-shipment
  "Validate + construct the MOLDING-RUN-BATCH-SHIPMENT registration
  DRAFT -- the injection-molding plant's own act of dispatching a real
  robot handling/shipment action releasing a molding-run-batch onward
  to a downstream consumer (the real dual upstream hand-off to BOTH
  `cloud-itonami-isic-2630`'s consumer-electronics device assembly and
  `cloud-itonami-isic-2910`/`cloud-itonami-isic-2920`'s motor-vehicle
  assembly -- see README `Upstream -> downstream hand-off`). Pure
  function -- does not touch any real plant/MES control system; it
  builds the RECORD an injection-molding plant would keep.
  `moldworks.governor` independently re-verifies the batch's own
  shrinkage-rate sufficiency against its own acceptance-band bounds,
  and a double-shipment for the same batch, before this is ever allowed
  to commit."
  [batch-id jurisdiction sequence]
  (when-not (and batch-id (not= batch-id ""))
    (throw (ex-info "molding-run-batch-shipment: batch_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "molding-run-batch-shipment: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "molding-run-batch-shipment: sequence must be >= 0" {})))
  (let [shipment-number (str (str/upper-case jurisdiction) "-MRB-" (zero-pad sequence 6))
        record {"record_id" shipment-number
                "kind" "molding-run-batch-shipment-draft"
                "batch_id" batch-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "shipment_number" shipment-number
     "certificate" (unsigned-certificate "MoldingRunBatchShipment" shipment-number shipment-number)}))

(defn register-material-certificate
  "Validate + construct the MATERIAL CERTIFICATE OF COMPLIANCE
  registration DRAFT -- the injection-molding plant's own act of
  issuing a real Material Certificate of Compliance certifying a
  molding-run-batch's material-spec conformance before onward shipment
  to either downstream consumer. Pure function -- does not touch any
  real plant/MES control system; it builds the RECORD an
  injection-molding plant would keep. `moldworks.governor`
  independently re-verifies the batch's own end-of-line-defect
  resolution status, and a double-issuance for the same batch, before
  this is ever allowed to commit."
  [batch-id jurisdiction sequence]
  (when-not (and batch-id (not= batch-id ""))
    (throw (ex-info "material-certificate: batch_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "material-certificate: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "material-certificate: sequence must be >= 0" {})))
  (let [certificate-number (str (str/upper-case jurisdiction) "-MCC-" (zero-pad sequence 6))
        record {"record_id" certificate-number
                "kind" "material-certificate-draft"
                "batch_id" batch-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "certificate_number" certificate-number
     "certificate" (unsigned-certificate "MaterialCertificateOfCompliance" certificate-number certificate-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
