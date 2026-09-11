(ns moldworks.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean molding-run-batch
  through intake -> material-spec requirements verification -> end-of-
  line-defect screening -> robot clamp-force-verification mission ->
  batch-shipment proposal (always escalates) -> human approval ->
  commit, then through Material-Certificate-of-Compliance proposal
  (always escalates) -> human approval -> commit, then shows five HARD
  holds (a product class with no spec-basis, an out-of-spec shrinkage-
  rate deviation, an unresolved end-of-line defect screened directly
  via `:end-of-line-quality/screen` [never via an actuation op against
  an unscreened batch -- see this actor's own governor ns docstring /
  the lesson `parksafety`'s ADR-2607071922 Decision 5 and every prior
  sibling's ADR-0001 already recorded], a robot clamp-force-
  verification mission missing, a real physics-2d-simulated clamp
  reading that independently re-checks under-clamped even though
  `:robotics-sim-verified?` was seeded true, and a double batch-
  shipment/certificate-issuance of an already-processed batch) that
  never reach a human at all, and prints the audit ledger + the draft
  batch-shipment and Material-Certificate-of-Compliance records."
  (:require [langgraph.graph :as g]
            [moldworks.export :as export]
            [moldworks.store :as store]
            [moldworks.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :quality-engineer :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== molding-run-batch/intake batch-1 (AUTOMOTIVE, clean; shrinkage within spec, no EOL defect) ==")
    (println (exec! actor "t1" {:op :molding-run-batch/intake :subject "batch-1"
                                :patch {:id "batch-1" :batch-name "Meridian Dash Bracket Molding Batch MB-4401"}} operator))

    (println "== material-spec-rules/verify batch-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :material-spec-rules/verify :subject "batch-1"} operator))
    (println (approve! actor "t2"))

    (println "== end-of-line-quality/screen batch-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :end-of-line-quality/screen :subject "batch-1"} operator))
    (println (approve! actor "t3"))

    (println "== robotics/simulate-clamp-force-test batch-1 (real physics-2d clamp mission; escalates -- human approves) ==")
    (println (exec! actor "t3b" {:op :robotics/simulate-clamp-force-test :subject "batch-1"} operator))
    (println (approve! actor "t3b"))

    (println "== actuation/ship-molding-run-batch batch-1 (always escalates -- actuation/ship-molding-run-batch) ==")
    (let [r (exec! actor "t4" {:op :actuation/ship-molding-run-batch :subject "batch-1"} operator)]
      (println r)
      (println "-- human quality engineer approves --")
      (println (approve! actor "t4")))

    (println "== actuation/issue-material-certificate batch-1 (always escalates -- actuation/issue-material-certificate) ==")
    (let [r (exec! actor "t5" {:op :actuation/issue-material-certificate :subject "batch-1"} operator)]
      (println r)
      (println "-- human quality engineer approves --")
      (println (approve! actor "t5")))

    (println "== material-spec-rules/verify batch-2 (no spec-basis, MEDDEV product class -> HARD hold) ==")
    (println (exec! actor "t6" {:op :material-spec-rules/verify :subject "batch-2" :no-spec? true} operator))

    (println "== material-spec-rules/verify batch-3 (escalates -- human approves; sets up the out-of-spec test) ==")
    (println (exec! actor "t7" {:op :material-spec-rules/verify :subject "batch-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/ship-molding-run-batch batch-3 before clamp-force simulation -> HARD hold (robotics-simulation-missing) ==")
    (println (exec! actor "t7b" {:op :actuation/ship-molding-run-batch :subject "batch-3"} operator))

    (println "== robotics/simulate-clamp-force-test batch-3 (clean clamp tonnage; escalates -- human approves) ==")
    (println (exec! actor "t7c" {:op :robotics/simulate-clamp-force-test :subject "batch-3"} operator))
    (println (approve! actor "t7c"))

    (println "== actuation/ship-molding-run-batch batch-3 (0.35% shrinkage-rate deviation outside [-0.15,0.15]% -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/ship-molding-run-batch :subject "batch-3"} operator))

    (println "== actuation/ship-molding-run-batch batch-5 (robotics-sim on file, but clamp tonnage independently re-checks under-clamped -> HARD hold) ==")
    (println (exec! actor "t8b" {:op :material-spec-rules/verify :subject "batch-5"} operator))
    (println (approve! actor "t8b"))
    (println (exec! actor "t8c" {:op :actuation/ship-molding-run-batch :subject "batch-5"} operator))

    (println "== end-of-line-quality/screen batch-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :end-of-line-quality/screen :subject "batch-4"} operator))

    (println "== actuation/ship-molding-run-batch batch-1 AGAIN (double-shipment -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/ship-molding-run-batch :subject "batch-1"} operator))

    (println "== actuation/issue-material-certificate batch-1 AGAIN (double-issuance -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/issue-material-certificate :subject "batch-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft molding-run-batch-shipment records ==")
    (doseq [r (store/shipment-history db)] (println r))

    (println "== draft Material Certificate of Compliance records ==")
    (doseq [r (store/certificate-history db)] (println r))

    (println "== social hand-off: audit package counts ==")
    (println (:counts (export/audit-package db)))
    (println "== social hand-off: CSV bundle keys ==")
    (println (keys (export/package->csv-bundle db)))))
