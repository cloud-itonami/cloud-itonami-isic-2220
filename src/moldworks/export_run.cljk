(ns moldworks.export-run
  "CLI: write a demo audit CSV bundle for social / regulatory hand-off.
  Usage: clojure -M:dev:export [out-dir]
  Default out-dir: out/audit-package"
  (:require [moldworks.store :as store]
            [moldworks.export :as export]
            [moldworks.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :quality-engineer :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "export-runner"}}
          {:thread-id tid :resume? true}))

(defn- seed-demo!
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "v" {:op :material-spec-rules/verify :subject "batch-1"})
    (approve! actor "v")
    (exec! actor "r" {:op :robotics/simulate-clamp-force-test :subject "batch-1"})
    (approve! actor "r")
    (exec! actor "d" {:op :actuation/ship-molding-run-batch :subject "batch-1"})
    (approve! actor "d")
    db))

(defn -main [& args]
  (let [dir (or (first args) "out/audit-package")
        db (seed-demo!)
        path (export/write-csv-bundle! db dir)
        pkg (export/audit-package db)]
    (println "wrote" path)
    (println "counts" (:counts pkg))
    (println "files" (vec (keys (export/package->csv-bundle db))))))
