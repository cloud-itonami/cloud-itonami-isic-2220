(ns moldworks.store
  "SSoT for the injection-molding-plant manufacturing actor, behind a
  `Store` protocol so the backend is a swap, not a rewrite -- the same
  seam every prior `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/moldworks/store_contract_test.clj), which is the whole point:
  the actor, the Clamp-Force Governor and the audit ledger never know
  which SSoT they run on.

  Like `cellworks.store`'s dual cell-batch-shipment/safety-certificate
  history and `glassworks.store`'s dual glass-panel-batch-shipment/
  glazing-certificate history, this actor has TWO actuation events
  (shipping a molding-run-batch onward to a downstream consumer,
  issuing a Material Certificate of Compliance) acting on the SAME
  entity (a molding-run-batch), each with its OWN history collection,
  sequence counter and dedicated double-actuation-guard boolean
  (`:molding-run-batch-shipped?`/`:material-certified?`, never a
  `:status` value) -- the same discipline every prior sibling
  governor's guards establish, informed by `cloud-itonami-isic-6492`'s
  status-lifecycle bug (ADR-2607071320).

  The ledger stays append-only on every backend: 'which molding-run-
  batch was screened for an unresolved end-of-line defect, which
  molding-run-batch shipment was dispatched onward to a downstream
  consumer, which Material Certificate of Compliance was issued, on
  what product-class basis, approved by whom' is always a query over
  an immutable log -- the audit trail a community trusting an
  injection-molding plant needs, and the evidence a plant needs if a
  shipment or certificate decision is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [moldworks.registry :as registry]
            [moldworks.robotics :as robotics]
            [langchain.db :as d]))

(defprotocol Store
  (molding-run-batch [s id])
  (all-molding-run-batches [s])
  (eol-screen-of [s batch-id] "committed end-of-line quality screening verdict for a batch, or nil")
  (material-spec-verification-of [s batch-id] "committed material-spec-rules evidence verification, or nil")
  (ledger [s])
  (shipment-history [s] "the append-only molding-run-batch-shipment history (moldworks.registry drafts)")
  (certificate-history [s] "the append-only Material Certificate of Compliance history (moldworks.registry drafts)")
  (next-shipment-sequence [s jurisdiction] "next shipment-number sequence for a product-class scheme")
  (next-certificate-sequence [s jurisdiction] "next certificate-number sequence for a product-class scheme")
  (batch-already-shipped? [s batch-id] "has this molding-run-batch already been shipped onward?")
  (batch-already-certified? [s batch-id] "has this molding-run-batch's Material Certificate of Compliance already been issued?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-molding-run-batches [s batches] "replace/seed the molding-run-batch directory (map id->batch)"))

;; ----------------------------- demo data -----------------------------

(defn- with-clamp-telemetry
  "Merges REAL clamp-force-verification telemetry onto a demo
  molding-run-batch's base fields -- `moldworks.robotics/clamp-
  telemetry-for` actually runs `simulate-clamp-force`'s
  `physics-2d`-stepped simulation for this batch's own
  `:clamp-unit-moving-platen-mass-kg` (ADR-2607151600/ADR-2607152000),
  so even the 'already on file' seed data (as if from an earlier real
  clamp-force verification report) is genuinely simulation-derived,
  never hand-typed doubles."
  [base]
  (merge base (select-keys (robotics/clamp-telemetry-for base)
                           [:sim-peak-clamp-force-n :sim-peak-clamp-tonnage])))

(defn demo-data
  "A small, self-contained molding-run-batch set covering both
  actuation lifecycles (shipping a batch onward to a downstream
  consumer, issuing a Material Certificate of Compliance) so the actor
  + tests run offline. `:clamp-unit-moving-platen-mass-kg`
  (ADR-2607151600/ADR-2607152000) is a permanent batch press-run-
  configuration field (like `:shrinkage-rate-deviation-actual-pct`);
  `:sim-peak-clamp-force-n`/`:sim-peak-clamp-tonnage` are the REAL
  `moldworks.robotics/simulate-clamp-force`-computed telemetry for that
  field (`with-clamp-telemetry`), the ground truth `moldworks.robotics/
  simulation-out-of-tolerance?` independently rechecks against the
  batch's own `:projected-part-area-in2`/`:cavity-pressure-factor-
  tons-per-in2`-derived `required-clamp-tonnage-tons`.

  batch-1 -- automotive interior structural bracket (engineering-grade
    glass-filled material, 120 in^2 projected area x 7.0 tons/in^2 =
    840 tons required; 90,000 kg moving-platen assembly clears it with
    margin) -- the clean, fully-processable automotive batch.
  batch-2 -- consumer-electronics smartphone back-cover housing
    (easy-flow material, 15 in^2 x 3.5 tons/in^2 = 52.5 tons required;
    6,000 kg moving-platen assembly clears it with margin), but
    recorded against a product class (\"MEDDEV\", a medical-device
    housing scheme) `moldworks.facts` genuinely does NOT cover --
    the no-spec-basis negative control.
  batch-3 -- automotive, clamp force clean, but its own recorded
    shrinkage-rate deviation (0.35 percentage points) falls outside its
    own [-0.15,0.15] acceptance band -- a genuine post-mold
    dimensional-QA defect distinct from the clamp-force physics check.
  batch-4 -- consumer-electronics (real, covered \"CE-HOUSING\" scheme),
    clamp force and shrinkage both clean, but an unresolved end-of-line
    defect (short-shot/flash visual reject) is on file.
  batch-5 -- automotive, DELIBERATELY recorded with a much lighter
    `:clamp-unit-moving-platen-mass-kg` (40,000 kg) than its own
    840-ton requirement can clear (real simulated clamp reading ~450
    tons) -- a genuine press-run-configuration inconsistency (this
    job was run on a smaller-tonnage-class press than the part's own
    projected-area/material spec actually requires, or the wrong
    press-run configuration was logged) that the real, re-run
    simulation catches on independent recheck even though
    `:robotics-sim-verified?` was seeded `true` (\"already on file\",
    i.e. someone/something marked it passed without this real check
    ever having run) -- the injection-molding-plant analog of
    automotive's misclassified vehicle-5 / cellworks' batch-5 /
    glassworks' batch-5."
  []
  {:molding-run-batches
   (into {}
         (map (fn [v] [(:id v) (with-clamp-telemetry v)]))
         [{:id "batch-1" :batch-name "Meridian Dash Bracket Molding Batch MB-4401"
           :material-class :engineering-grade
           :projected-part-area-in2 120.0
           :cavity-pressure-factor-tons-per-in2 7.0
           :clamp-unit-moving-platen-mass-kg 90000
           :shrinkage-rate-deviation-actual-pct 0.05
           :shrinkage-rate-deviation-min-pct -0.15
           :shrinkage-rate-deviation-max-pct 0.15
           :molding-run-batch-defect-unresolved? false
           :robotics-sim-verified? false :robotics-sim-record nil
           :molding-run-batch-shipped? false :material-certified? false
           :jurisdiction "AUTOMOTIVE" :status :intake}
          {:id "batch-2" :batch-name "Atlas Back-Cover Housing Molding Batch MB-1180"
           :material-class :easy-flow
           :projected-part-area-in2 15.0
           :cavity-pressure-factor-tons-per-in2 3.5
           :clamp-unit-moving-platen-mass-kg 6000
           :shrinkage-rate-deviation-actual-pct 0.05
           :shrinkage-rate-deviation-min-pct -0.15
           :shrinkage-rate-deviation-max-pct 0.15
           :molding-run-batch-defect-unresolved? false
           :robotics-sim-verified? false :robotics-sim-record nil
           :molding-run-batch-shipped? false :material-certified? false
           :jurisdiction "MEDDEV" :status :intake}
          {:id "batch-3" :batch-name "田中バンパーフェイシア成形バッチ MB-2215"
           :material-class :engineering-grade
           :projected-part-area-in2 120.0
           :cavity-pressure-factor-tons-per-in2 7.0
           :clamp-unit-moving-platen-mass-kg 90000
           :shrinkage-rate-deviation-actual-pct 0.35
           :shrinkage-rate-deviation-min-pct -0.15
           :shrinkage-rate-deviation-max-pct 0.15
           :molding-run-batch-defect-unresolved? false
           :robotics-sim-verified? false :robotics-sim-record nil
           :molding-run-batch-shipped? false :material-certified? false
           :jurisdiction "AUTOMOTIVE" :status :intake}
          {:id "batch-4" :batch-name "佐藤スマートフォン筐体成形バッチ MB-3330"
           :material-class :easy-flow
           :projected-part-area-in2 15.0
           :cavity-pressure-factor-tons-per-in2 3.5
           :clamp-unit-moving-platen-mass-kg 6000
           :shrinkage-rate-deviation-actual-pct 0.05
           :shrinkage-rate-deviation-min-pct -0.15
           :shrinkage-rate-deviation-max-pct 0.15
           :molding-run-batch-defect-unresolved? true
           :robotics-sim-verified? false :robotics-sim-record nil
           :molding-run-batch-shipped? false :material-certified? false
           :jurisdiction "CE-HOUSING" :status :intake}
          {:id "batch-5" :batch-name "鈴木ダッシュブラケット成形バッチ MB-1118"
           :material-class :engineering-grade
           :projected-part-area-in2 120.0
           :cavity-pressure-factor-tons-per-in2 7.0
           :clamp-unit-moving-platen-mass-kg 40000
           :shrinkage-rate-deviation-actual-pct 0.05
           :shrinkage-rate-deviation-min-pct -0.15
           :shrinkage-rate-deviation-max-pct 0.15
           :molding-run-batch-defect-unresolved? false
           :robotics-sim-verified? true :robotics-sim-record nil
           :molding-run-batch-shipped? false :material-certified? false
           :jurisdiction "AUTOMOTIVE" :status :intake}])})

;; ----------------------------- shared commit logic -----------------------------

(defn- ship-molding-run-batch!
  "Backend-agnostic `:molding-run-batch/mark-shipped` -- looks up the
  batch via the protocol and drafts the molding-run-batch-shipment
  record, and returns {:result .. :batch-patch ..} for the caller to
  persist."
  [s batch-id]
  (let [a (molding-run-batch s batch-id)
        seq-n (next-shipment-sequence s (:jurisdiction a))
        result (registry/register-molding-run-batch-shipment batch-id (:jurisdiction a) seq-n)]
    {:result result
     :batch-patch {:molding-run-batch-shipped? true
                  :shipment-number (get result "shipment_number")}}))

(defn- issue-material-certificate!
  "Backend-agnostic `:molding-run-batch/mark-certified` -- looks up the
  batch via the protocol and drafts the Material Certificate of
  Compliance record, and returns {:result .. :batch-patch ..} for the
  caller to persist."
  [s batch-id]
  (let [a (molding-run-batch s batch-id)
        seq-n (next-certificate-sequence s (:jurisdiction a))
        result (registry/register-material-certificate batch-id (:jurisdiction a) seq-n)]
    {:result result
     :batch-patch {:material-certified? true
                  :certificate-number (get result "certificate_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (molding-run-batch [_ id] (get-in @a [:molding-run-batches id]))
  (all-molding-run-batches [_] (sort-by :id (vals (:molding-run-batches @a))))
  (eol-screen-of [_ id] (get-in @a [:eol-screens id]))
  (material-spec-verification-of [_ batch-id] (get-in @a [:verifications batch-id]))
  (ledger [_] (:ledger @a))
  (shipment-history [_] (:shipments @a))
  (certificate-history [_] (:certificates @a))
  (next-shipment-sequence [_ jurisdiction] (get-in @a [:shipment-sequences jurisdiction] 0))
  (next-certificate-sequence [_ jurisdiction] (get-in @a [:certificate-sequences jurisdiction] 0))
  (batch-already-shipped? [_ batch-id] (boolean (get-in @a [:molding-run-batches batch-id :molding-run-batch-shipped?])))
  (batch-already-certified? [_ batch-id] (boolean (get-in @a [:molding-run-batches batch-id :material-certified?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :molding-run-batch/upsert
      (swap! a update-in [:molding-run-batches (:id value)] merge value)

      :material-spec-verification/set
      (swap! a assoc-in [:verifications (first path)] payload)

      :eol-screen/set
      (swap! a assoc-in [:eol-screens (first path)] payload)

      :molding-run-batch/mark-shipped
      (let [batch-id (first path)
            {:keys [result batch-patch]} (ship-molding-run-batch! s batch-id)
            jurisdiction (:jurisdiction (molding-run-batch s batch-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:shipment-sequences jurisdiction] (fnil inc 0))
                       (update-in [:molding-run-batches batch-id] merge batch-patch)
                       (update :shipments registry/append result))))
        result)

      :molding-run-batch/mark-certified
      (let [batch-id (first path)
            {:keys [result batch-patch]} (issue-material-certificate! s batch-id)
            jurisdiction (:jurisdiction (molding-run-batch s batch-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:certificate-sequences jurisdiction] (fnil inc 0))
                       (update-in [:molding-run-batches batch-id] merge batch-patch)
                       (update :certificates registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-molding-run-batches [s batches] (when (seq batches) (swap! a assoc :molding-run-batches batches)) s))

(defn seed-db
  "A MemStore seeded with the demo molding-run-batch set. The
  deterministic default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :verifications {} :eol-screens {} :ledger []
                           :shipment-sequences {} :shipments []
                           :certificate-sequences {} :certificates []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (verification/eol-screen payloads, ledger facts,
  shipment/certificate records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:molding-run-batch/id              {:db/unique :db.unique/identity}
   :verification/batch-id             {:db/unique :db.unique/identity}
   :eol-screen/batch-id               {:db/unique :db.unique/identity}
   :ledger/seq                        {:db/unique :db.unique/identity}
   :shipment/seq                      {:db/unique :db.unique/identity}
   :certificate/seq                   {:db/unique :db.unique/identity}
   :shipment-sequence/jurisdiction    {:db/unique :db.unique/identity}
   :certificate-sequence/jurisdiction {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- batch->tx [{:keys [id batch-name material-class
                          projected-part-area-in2 cavity-pressure-factor-tons-per-in2
                          clamp-unit-moving-platen-mass-kg sim-peak-clamp-force-n sim-peak-clamp-tonnage
                          shrinkage-rate-deviation-actual-pct shrinkage-rate-deviation-min-pct shrinkage-rate-deviation-max-pct
                          molding-run-batch-defect-unresolved? robotics-sim-verified? robotics-sim-record
                          molding-run-batch-shipped? material-certified?
                          jurisdiction status shipment-number certificate-number]}]
  (cond-> {:molding-run-batch/id id}
    batch-name                                        (assoc :molding-run-batch/batch-name batch-name)
    material-class                                     (assoc :molding-run-batch/material-class material-class)
    projected-part-area-in2                            (assoc :molding-run-batch/projected-part-area-in2 projected-part-area-in2)
    cavity-pressure-factor-tons-per-in2                (assoc :molding-run-batch/cavity-pressure-factor-tons-per-in2 cavity-pressure-factor-tons-per-in2)
    clamp-unit-moving-platen-mass-kg                   (assoc :molding-run-batch/clamp-unit-moving-platen-mass-kg clamp-unit-moving-platen-mass-kg)
    sim-peak-clamp-force-n                             (assoc :molding-run-batch/sim-peak-clamp-force-n sim-peak-clamp-force-n)
    (some? sim-peak-clamp-tonnage)                      (assoc :molding-run-batch/sim-peak-clamp-tonnage sim-peak-clamp-tonnage)
    shrinkage-rate-deviation-actual-pct                (assoc :molding-run-batch/shrinkage-rate-deviation-actual-pct shrinkage-rate-deviation-actual-pct)
    shrinkage-rate-deviation-min-pct                   (assoc :molding-run-batch/shrinkage-rate-deviation-min-pct shrinkage-rate-deviation-min-pct)
    shrinkage-rate-deviation-max-pct                   (assoc :molding-run-batch/shrinkage-rate-deviation-max-pct shrinkage-rate-deviation-max-pct)
    (some? molding-run-batch-defect-unresolved?)       (assoc :molding-run-batch/defect-unresolved? molding-run-batch-defect-unresolved?)
    (some? robotics-sim-verified?)                     (assoc :molding-run-batch/robotics-sim-verified? robotics-sim-verified?)
    (some? robotics-sim-record)                        (assoc :molding-run-batch/robotics-sim-record (enc robotics-sim-record))
    (some? molding-run-batch-shipped?)                 (assoc :molding-run-batch/shipped? molding-run-batch-shipped?)
    (some? material-certified?)                        (assoc :molding-run-batch/material-certified? material-certified?)
    jurisdiction                                        (assoc :molding-run-batch/jurisdiction jurisdiction)
    status                                              (assoc :molding-run-batch/status status)
    shipment-number                                     (assoc :molding-run-batch/shipment-number shipment-number)
    certificate-number                                  (assoc :molding-run-batch/certificate-number certificate-number)))

(def ^:private batch-pull
  [:molding-run-batch/id :molding-run-batch/batch-name :molding-run-batch/material-class
   :molding-run-batch/projected-part-area-in2 :molding-run-batch/cavity-pressure-factor-tons-per-in2
   :molding-run-batch/clamp-unit-moving-platen-mass-kg :molding-run-batch/sim-peak-clamp-force-n :molding-run-batch/sim-peak-clamp-tonnage
   :molding-run-batch/shrinkage-rate-deviation-actual-pct :molding-run-batch/shrinkage-rate-deviation-min-pct :molding-run-batch/shrinkage-rate-deviation-max-pct
   :molding-run-batch/defect-unresolved? :molding-run-batch/robotics-sim-verified? :molding-run-batch/robotics-sim-record
   :molding-run-batch/shipped? :molding-run-batch/material-certified?
   :molding-run-batch/jurisdiction :molding-run-batch/status :molding-run-batch/shipment-number :molding-run-batch/certificate-number])

(defn- pull->batch [m]
  (when (:molding-run-batch/id m)
    {:id (:molding-run-batch/id m) :batch-name (:molding-run-batch/batch-name m)
     :material-class (:molding-run-batch/material-class m)
     :projected-part-area-in2 (:molding-run-batch/projected-part-area-in2 m)
     :cavity-pressure-factor-tons-per-in2 (:molding-run-batch/cavity-pressure-factor-tons-per-in2 m)
     :clamp-unit-moving-platen-mass-kg (:molding-run-batch/clamp-unit-moving-platen-mass-kg m)
     :sim-peak-clamp-force-n (:molding-run-batch/sim-peak-clamp-force-n m)
     :sim-peak-clamp-tonnage (:molding-run-batch/sim-peak-clamp-tonnage m)
     :shrinkage-rate-deviation-actual-pct (:molding-run-batch/shrinkage-rate-deviation-actual-pct m)
     :shrinkage-rate-deviation-min-pct (:molding-run-batch/shrinkage-rate-deviation-min-pct m)
     :shrinkage-rate-deviation-max-pct (:molding-run-batch/shrinkage-rate-deviation-max-pct m)
     :molding-run-batch-defect-unresolved? (boolean (:molding-run-batch/defect-unresolved? m))
     :robotics-sim-verified? (boolean (:molding-run-batch/robotics-sim-verified? m))
     :robotics-sim-record (dec* (:molding-run-batch/robotics-sim-record m))
     :molding-run-batch-shipped? (boolean (:molding-run-batch/shipped? m))
     :material-certified? (boolean (:molding-run-batch/material-certified? m))
     :jurisdiction (:molding-run-batch/jurisdiction m) :status (:molding-run-batch/status m)
     :shipment-number (:molding-run-batch/shipment-number m) :certificate-number (:molding-run-batch/certificate-number m)}))

(defrecord DatomicStore [conn]
  Store
  (molding-run-batch [_ id]
    (pull->batch (d/pull (d/db conn) batch-pull [:molding-run-batch/id id])))
  (all-molding-run-batches [_]
    (->> (d/q '[:find [?id ...] :where [?e :molding-run-batch/id ?id]] (d/db conn))
         (map #(pull->batch (d/pull (d/db conn) batch-pull [:molding-run-batch/id %])))
         (sort-by :id)))
  (eol-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?k :eol-screen/batch-id ?aid] [?k :eol-screen/payload ?p]]
              (d/db conn) id)))
  (material-spec-verification-of [_ batch-id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?a :verification/batch-id ?aid] [?a :verification/payload ?p]]
              (d/db conn) batch-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (shipment-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :shipment/seq ?s] [?e :shipment/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (certificate-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :certificate/seq ?s] [?e :certificate/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-shipment-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :shipment-sequence/jurisdiction ?j] [?e :shipment-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-certificate-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :certificate-sequence/jurisdiction ?j] [?e :certificate-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (batch-already-shipped? [s batch-id]
    (boolean (:molding-run-batch-shipped? (molding-run-batch s batch-id))))
  (batch-already-certified? [s batch-id]
    (boolean (:material-certified? (molding-run-batch s batch-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :molding-run-batch/upsert
      (d/transact! conn [(batch->tx value)])

      :material-spec-verification/set
      (d/transact! conn [{:verification/batch-id (first path) :verification/payload (enc payload)}])

      :eol-screen/set
      (d/transact! conn [{:eol-screen/batch-id (first path) :eol-screen/payload (enc payload)}])

      :molding-run-batch/mark-shipped
      (let [batch-id (first path)
            {:keys [result batch-patch]} (ship-molding-run-batch! s batch-id)
            jurisdiction (:jurisdiction (molding-run-batch s batch-id))
            next-n (inc (next-shipment-sequence s jurisdiction))]
        (d/transact! conn
                     [(batch->tx (assoc batch-patch :id batch-id))
                      {:shipment-sequence/jurisdiction jurisdiction :shipment-sequence/next next-n}
                      {:shipment/seq (count (shipment-history s)) :shipment/record (enc (get result "record"))}])
        result)

      :molding-run-batch/mark-certified
      (let [batch-id (first path)
            {:keys [result batch-patch]} (issue-material-certificate! s batch-id)
            jurisdiction (:jurisdiction (molding-run-batch s batch-id))
            next-n (inc (next-certificate-sequence s jurisdiction))]
        (d/transact! conn
                     [(batch->tx (assoc batch-patch :id batch-id))
                      {:certificate-sequence/jurisdiction jurisdiction :certificate-sequence/next next-n}
                      {:certificate/seq (count (certificate-history s)) :certificate/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-molding-run-batches [s batches]
    (when (seq batches) (d/transact! conn (mapv batch->tx (vals batches)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:molding-run-batches ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [molding-run-batches]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-molding-run-batches s molding-run-batches))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo molding-run-batch set -- the
  Datomic-backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
