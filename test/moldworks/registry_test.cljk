(ns moldworks.registry-test
  (:require [clojure.test :refer [deftest is]]
            [moldworks.registry :as r]))

;; ----------------------------- molding-run-batch-shrinkage-out-of-range? -----------------------------

(deftest not-out-of-range-when-within-bounds
  (is (not (r/molding-run-batch-shrinkage-out-of-range? {:shrinkage-rate-deviation-actual-pct 0.05 :shrinkage-rate-deviation-min-pct -0.15 :shrinkage-rate-deviation-max-pct 0.15})))
  (is (not (r/molding-run-batch-shrinkage-out-of-range? {:shrinkage-rate-deviation-actual-pct -0.15 :shrinkage-rate-deviation-min-pct -0.15 :shrinkage-rate-deviation-max-pct 0.15})))
  (is (not (r/molding-run-batch-shrinkage-out-of-range? {:shrinkage-rate-deviation-actual-pct 0.15 :shrinkage-rate-deviation-min-pct -0.15 :shrinkage-rate-deviation-max-pct 0.15}))))

(deftest out-of-range-when-below-minimum-or-above-maximum
  (is (r/molding-run-batch-shrinkage-out-of-range? {:shrinkage-rate-deviation-actual-pct -0.20 :shrinkage-rate-deviation-min-pct -0.15 :shrinkage-rate-deviation-max-pct 0.15}))
  (is (r/molding-run-batch-shrinkage-out-of-range? {:shrinkage-rate-deviation-actual-pct 0.35 :shrinkage-rate-deviation-min-pct -0.15 :shrinkage-rate-deviation-max-pct 0.15})))

(deftest out-of-range-is-false-on-missing-fields
  (is (not (r/molding-run-batch-shrinkage-out-of-range? {})))
  (is (not (r/molding-run-batch-shrinkage-out-of-range? {:shrinkage-rate-deviation-actual-pct 0.35}))))

;; ----------------------------- register-molding-run-batch-shipment -----------------------------

(deftest shipment-is-a-draft-not-a-real-shipment
  (let [result (r/register-molding-run-batch-shipment "batch-1" "AUTOMOTIVE" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest shipment-assigns-shipment-number
  (let [result (r/register-molding-run-batch-shipment "batch-1" "AUTOMOTIVE" 7)]
    (is (= (get result "shipment_number") "AUTOMOTIVE-MRB-000007"))
    (is (= (get-in result ["record" "batch_id"]) "batch-1"))
    (is (= (get-in result ["record" "kind"]) "molding-run-batch-shipment-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest shipment-validation-rules
  (is (thrown? Exception (r/register-molding-run-batch-shipment "" "AUTOMOTIVE" 0)))
  (is (thrown? Exception (r/register-molding-run-batch-shipment "batch-1" "" 0)))
  (is (thrown? Exception (r/register-molding-run-batch-shipment "batch-1" "AUTOMOTIVE" -1))))

;; ----------------------------- register-material-certificate -----------------------------

(deftest certificate-is-a-draft-not-real-certification
  (let [result (r/register-material-certificate "batch-1" "AUTOMOTIVE" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest certificate-assigns-certificate-number
  (let [result (r/register-material-certificate "batch-1" "AUTOMOTIVE" 3)]
    (is (= (get result "certificate_number") "AUTOMOTIVE-MCC-000003"))
    (is (= (get-in result ["record" "batch_id"]) "batch-1"))
    (is (= (get-in result ["record" "kind"]) "material-certificate-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest certificate-validation-rules
  (is (thrown? Exception (r/register-material-certificate "" "AUTOMOTIVE" 0)))
  (is (thrown? Exception (r/register-material-certificate "batch-1" "" 0)))
  (is (thrown? Exception (r/register-material-certificate "batch-1" "AUTOMOTIVE" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-molding-run-batch-shipment "batch-1" "AUTOMOTIVE" 0)
        hist (r/append [] c1)
        c2 (r/register-molding-run-batch-shipment "batch-2" "AUTOMOTIVE" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "AUTOMOTIVE-MRB-000000" (get-in hist2 [0 "record_id"])))
    (is (= "AUTOMOTIVE-MRB-000001" (get-in hist2 [1 "record_id"])))))
