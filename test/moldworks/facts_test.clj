(ns moldworks.facts-test
  (:require [clojure.test :refer [deftest is]]
            [moldworks.facts :as facts]))

(deftest automotive-has-a-spec-basis
  (is (some? (facts/spec-basis "AUTOMOTIVE")))
  (is (string? (:provenance (facts/spec-basis "AUTOMOTIVE")))))

(deftest ce-housing-has-a-spec-basis
  (is (some? (facts/spec-basis "CE-HOUSING"))))

(deftest unknown-product-class-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "MEDDEV"))))

(deftest coverage-never-reports-a-missing-product-class-as-covered
  (let [report (facts/coverage ["AUTOMOTIVE" "MEDDEV" "CE-HOUSING"])]
    (is (= 2 (:covered report)))
    (is (= ["MEDDEV"] (:missing-jurisdictions report)))
    (is (= ["AUTOMOTIVE" "CE-HOUSING"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "AUTOMOTIVE")]
    (is (facts/required-evidence-satisfied? "AUTOMOTIVE" all))
    (is (not (facts/required-evidence-satisfied? "AUTOMOTIVE" (rest all))))
    (is (not (facts/required-evidence-satisfied? "MEDDEV" all)) "no spec-basis -> never satisfied")))
