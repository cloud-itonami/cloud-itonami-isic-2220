(ns moldworks.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [moldworks.robotics :as robotics]
            [moldworks.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Meridian Dash Bracket Molding Batch MB-4401" (:batch-name (store/molding-run-batch s "batch-1"))))
      (is (= "AUTOMOTIVE" (:jurisdiction (store/molding-run-batch s "batch-1"))))
      (is (= 0.05 (:shrinkage-rate-deviation-actual-pct (store/molding-run-batch s "batch-1"))))
      (is (= -0.15 (:shrinkage-rate-deviation-min-pct (store/molding-run-batch s "batch-1"))))
      (is (= 0.15 (:shrinkage-rate-deviation-max-pct (store/molding-run-batch s "batch-1"))))
      (is (false? (:molding-run-batch-defect-unresolved? (store/molding-run-batch s "batch-1"))))
      (is (= 0.35 (:shrinkage-rate-deviation-actual-pct (store/molding-run-batch s "batch-3"))))
      (is (true? (:molding-run-batch-defect-unresolved? (store/molding-run-batch s "batch-4"))))
      (is (false? (:robotics-sim-verified? (store/molding-run-batch s "batch-1"))) "no robotics mission has run yet")
      (is (true? (:robotics-sim-verified? (store/molding-run-batch s "batch-5"))) "seeded as already-on-file")
      (is (= 40000 (:clamp-unit-moving-platen-mass-kg (store/molding-run-batch s "batch-5"))))
      (is (< (:sim-peak-clamp-tonnage (store/molding-run-batch s "batch-5"))
             (robotics/required-clamp-tonnage-tons (store/molding-run-batch s "batch-5")))
          "batch-5's real physics-2d-simulated clamp tonnage falls short of its own required-clamp-tonnage-tons")
      (is (> (:sim-peak-clamp-tonnage (store/molding-run-batch s "batch-1"))
             (robotics/required-clamp-tonnage-tons (store/molding-run-batch s "batch-1")))
          "batch-1's real physics-2d-simulated clamp tonnage clears its own required-clamp-tonnage-tons")
      (is (= 9000000.0 (:sim-peak-clamp-force-n (store/molding-run-batch s "batch-1"))))
      (is (= 1011.6402439486973 (:sim-peak-clamp-tonnage (store/molding-run-batch s "batch-1"))))
      (is (= 4000000.0 (:sim-peak-clamp-force-n (store/molding-run-batch s "batch-5"))))
      (is (= 449.617886199421 (:sim-peak-clamp-tonnage (store/molding-run-batch s "batch-5"))))
      (is (= 840.0 (robotics/required-clamp-tonnage-tons (store/molding-run-batch s "batch-1"))))
      (is (= 52.5 (robotics/required-clamp-tonnage-tons (store/molding-run-batch s "batch-2"))))
      (is (false? (:molding-run-batch-shipped? (store/molding-run-batch s "batch-1"))))
      (is (false? (:material-certified? (store/molding-run-batch s "batch-1"))))
      (is (= ["batch-1" "batch-2" "batch-3" "batch-4" "batch-5"]
             (mapv :id (store/all-molding-run-batches s))))
      (is (nil? (store/eol-screen-of s "batch-1")))
      (is (nil? (store/material-spec-verification-of s "batch-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/shipment-history s)))
      (is (= [] (store/certificate-history s)))
      (is (zero? (store/next-shipment-sequence s "AUTOMOTIVE")))
      (is (zero? (store/next-certificate-sequence s "AUTOMOTIVE")))
      (is (false? (store/batch-already-shipped? s "batch-1")))
      (is (false? (store/batch-already-certified? s "batch-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :molding-run-batch/upsert
                                 :value {:id "batch-1" :batch-name "Meridian Dash Bracket Molding Batch MB-4401"}})
        (is (= "Meridian Dash Bracket Molding Batch MB-4401" (:batch-name (store/molding-run-batch s "batch-1"))))
        (is (= "AUTOMOTIVE" (:jurisdiction (store/molding-run-batch s "batch-1"))) "unrelated field preserved"))
      (testing "robotics-sim result commits via :molding-run-batch/upsert and reads back"
        (store/commit-record! s {:effect :molding-run-batch/upsert
                                 :value {:id "batch-1" :robotics-sim-verified? true
                                        :robotics-sim-record {:mission-id "m-1" :passed? true}}})
        (is (true? (:robotics-sim-verified? (store/molding-run-batch s "batch-1"))))
        (is (= {:mission-id "m-1" :passed? true} (:robotics-sim-record (store/molding-run-batch s "batch-1"))))
        (is (= "AUTOMOTIVE" (:jurisdiction (store/molding-run-batch s "batch-1"))) "unrelated field still preserved"))
      (testing "verification / eol-screen payloads commit and read back"
        (store/commit-record! s {:effect :material-spec-verification/set :path ["batch-1"]
                                 :payload {:jurisdiction "AUTOMOTIVE" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "AUTOMOTIVE" :checklist ["a" "b"]} (store/material-spec-verification-of s "batch-1")))
        (store/commit-record! s {:effect :eol-screen/set :path ["batch-1"]
                                 :payload {:batch-id "batch-1" :verdict :resolved}})
        (is (= {:batch-id "batch-1" :verdict :resolved} (store/eol-screen-of s "batch-1"))))
      (testing "molding-run-batch shipment drafts a record and advances the sequence"
        (store/commit-record! s {:effect :molding-run-batch/mark-shipped :path ["batch-1"]})
        (is (= "AUTOMOTIVE-MRB-000000" (get (first (store/shipment-history s)) "record_id")))
        (is (= "molding-run-batch-shipment-draft" (get (first (store/shipment-history s)) "kind")))
        (is (true? (:molding-run-batch-shipped? (store/molding-run-batch s "batch-1"))))
        (is (= 1 (count (store/shipment-history s))))
        (is (= 1 (store/next-shipment-sequence s "AUTOMOTIVE")))
        (is (true? (store/batch-already-shipped? s "batch-1")))
        (is (false? (store/batch-already-shipped? s "batch-2"))))
      (testing "Material Certificate of Compliance drafts a record and advances the sequence"
        (store/commit-record! s {:effect :molding-run-batch/mark-certified :path ["batch-1"]})
        (is (= "AUTOMOTIVE-MCC-000000" (get (first (store/certificate-history s)) "record_id")))
        (is (= "material-certificate-draft" (get (first (store/certificate-history s)) "kind")))
        (is (true? (:material-certified? (store/molding-run-batch s "batch-1"))))
        (is (= 1 (count (store/certificate-history s))))
        (is (= 1 (store/next-certificate-sequence s "AUTOMOTIVE")))
        (is (true? (store/batch-already-certified? s "batch-1")))
        (is (false? (store/batch-already-certified? s "batch-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/molding-run-batch s "nope")))
    (is (= [] (store/all-molding-run-batches s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/shipment-history s)))
    (is (= [] (store/certificate-history s)))
    (is (zero? (store/next-shipment-sequence s "AUTOMOTIVE")))
    (is (zero? (store/next-certificate-sequence s "AUTOMOTIVE")))
    (store/with-molding-run-batches s {"x" {:id "x" :batch-name "n"
                                     :shrinkage-rate-deviation-actual-pct 0.05
                                     :shrinkage-rate-deviation-min-pct -0.15
                                     :shrinkage-rate-deviation-max-pct 0.15
                                     :molding-run-batch-defect-unresolved? false
                                     :molding-run-batch-shipped? false :material-certified? false
                                     :jurisdiction "AUTOMOTIVE" :status :intake}})
    (is (= "n" (:batch-name (store/molding-run-batch s "x"))))))
