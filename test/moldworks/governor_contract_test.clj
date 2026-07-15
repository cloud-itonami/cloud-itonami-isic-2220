(ns moldworks.governor-contract-test
  "The governor contract as executable tests -- the injection-molding-
  plant analog of `cloud-itonami-isic-6512`'s `casualty.governor-
  contract-test`. The single invariant under test:

    Molding Advisor never ships a molding-run-batch action or issues a
    Material Certificate of Compliance the Clamp-Force Governor would
    reject, `:actuation/ship-molding-run-batch`/`:actuation/issue-
    material-certificate` NEVER auto-commit at any phase,
    `:molding-run-batch/intake` (no direct capital risk) MAY
    auto-commit when clean, and every decision (commit OR hold) leaves
    exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [moldworks.store :as store]
            [moldworks.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :quality-engineer :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving a material-spec-
  rules evidence verification on file. Uses distinct thread-ids per
  call site by suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :material-spec-rules/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- screen!
  "Walks `subject` through end-of-line quality screening -> approve,
  leaving a screening on file. Only safe to call for a batch whose
  defect status has already resolved -- an unresolved defect HARD-holds
  the screen itself (see `end-of-line-defect-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :end-of-line-quality/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(defn- simulate-robotics!
  "Walks `subject` through the robot clamp-force-verification mission
  -> approve, leaving `:robotics-sim-verified?` on file. Only
  meaningful to call for a batch whose REAL `physics-2d`-simulated
  clamp reading (`:sim-peak-clamp-tonnage`) actually clears its own
  `required-clamp-tonnage-tons` -- a batch whose clamp-run
  configuration falls short still gets :robotics-sim-verified? recorded
  (per whatever the mission itself found), but `moldworks.governor`'s
  independent recheck HARD-holds regardless (see
  `robotics-simulation-out-of-tolerance-is-held`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-robotics") {:op :robotics/simulate-clamp-force-test :subject subject} operator)
  (approve! actor (str tid-prefix "-robotics")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :molding-run-batch/intake :subject "batch-1"
                   :patch {:id "batch-1" :batch-name "Meridian Dash Bracket Molding Batch MB-4401"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Meridian Dash Bracket Molding Batch MB-4401" (:batch-name (store/molding-run-batch db "batch-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest requirements-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :material-spec-rules/verify :subject "batch-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/material-spec-verification-of db "batch-1")))))))

(deftest fabricated-product-class-is-held
  (testing "a material-spec-rules/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :material-spec-rules/verify :subject "batch-2" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/material-spec-verification-of db "batch-2")) "no verification written"))))

(deftest ship-molding-run-batch-without-verification-is-held
  (testing "actuation/ship-molding-run-batch before any material-spec-rules evidence verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/ship-molding-run-batch :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest molding-run-batch-shrinkage-out-of-range-is-held
  (testing "a batch whose own shrinkage-rate deviation falls outside its own acceptance-band bounds -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "batch-3")
          _ (simulate-robotics! actor "t5pre2" "batch-3")
          res (exec-op actor "t5" {:op :actuation/ship-molding-run-batch :subject "batch-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:molding-run-batch-shrinkage-out-of-range} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest end-of-line-defect-is-held-and-unoverridable
  (testing "an unresolved end-of-line defect on a batch -> HOLD, and never reaches request-approval -- exercised via :end-of-line-quality/screen DIRECTLY, not via the actuation op against an unscreened batch (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / every prior sibling's ADR-0001)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :end-of-line-quality/screen :subject "batch-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:end-of-line-defect-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/eol-screen-of db "batch-4")) "no clearance written"))))

(deftest ship-molding-run-batch-always-escalates-then-human-decides
  (testing "a clean, fully-verified, in-spec batch still ALWAYS interrupts for human approval -- actuation/ship-molding-run-batch is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "batch-1")
          _ (simulate-robotics! actor "t7pre2" "batch-1")
          r1 (exec-op actor "t7" {:op :actuation/ship-molding-run-batch :subject "batch-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, shipment record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:molding-run-batch-shipped? (store/molding-run-batch db "batch-1"))))
          (is (= 1 (count (store/shipment-history db))) "one draft shipment record"))))))

(deftest issue-material-certificate-always-escalates-then-human-decides
  (testing "a clean, fully-verified, resolved-defect batch still ALWAYS interrupts for human approval -- actuation/issue-material-certificate is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "batch-1")
          _ (screen! actor "t8pre2" "batch-1")
          r1 (exec-op actor "t8" {:op :actuation/issue-material-certificate :subject "batch-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, certificate record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:material-certified? (store/molding-run-batch db "batch-1"))))
          (is (= 1 (count (store/certificate-history db))) "one draft certificate record"))))))

(deftest ship-molding-run-batch-double-shipment-is-held
  (testing "shipping the same batch's action twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "batch-1")
          _ (simulate-robotics! actor "t9pre2" "batch-1")
          _ (exec-op actor "t9a" {:op :actuation/ship-molding-run-batch :subject "batch-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :actuation/ship-molding-run-batch :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-shipped} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/shipment-history db))) "still only the one earlier shipment"))))

(deftest issue-material-certificate-double-issuance-is-held
  (testing "issuing the same batch's Material Certificate of Compliance twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "batch-1")
          _ (screen! actor "t10pre2" "batch-1")
          _ (exec-op actor "t10a" {:op :actuation/issue-material-certificate :subject "batch-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :actuation/issue-material-certificate :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-certified} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/certificate-history db))) "still only the one earlier certificate issuance"))))

(deftest robotics-simulation-always-needs-approval
  (testing "robotics/simulate-clamp-force-test is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t11" {:op :robotics/simulate-clamp-force-test :subject "batch-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t11")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:robotics-sim-verified? (store/molding-run-batch db "batch-1"))))))))

(deftest ship-molding-run-batch-without-robotics-simulation-is-held
  (testing "actuation/ship-molding-run-batch before the robot clamp-force-verification mission ever ran -> HOLD (robotics-simulation-missing)"
    (let [[db actor] (fresh)
          _ (verify! actor "t12pre" "batch-1")
          res (exec-op actor "t12" {:op :actuation/ship-molding-run-batch :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:robotics-simulation-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest robotics-simulation-out-of-tolerance-is-held
  (testing "batch-5 has a robotics-sim already on file, but its own REAL physics-2d-simulated clamp-tonnage reading falls short of its own required-clamp-tonnage-tons on INDEPENDENT recheck -> HOLD, never trusts the on-file verdict alone (genuine under-clamped press-run-configuration fixture, real F=m*a simulation, not a hand-set field)"
    (let [[db actor] (fresh)
          _ (verify! actor "t13pre" "batch-5")
          res (exec-op actor "t13" {:op :actuation/ship-molding-run-batch :subject "batch-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:robotics-simulation-out-of-tolerance} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :molding-run-batch/intake :subject "batch-1"
                          :patch {:id "batch-1" :batch-name "Meridian Dash Bracket Molding Batch MB-4401"}} operator)
      (exec-op actor "b" {:op :material-spec-rules/verify :subject "batch-2" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
