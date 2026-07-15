(ns moldworks.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/ship-molding-run-batch`/`:actuation/issue-
  material-certificate` must NEVER be a member of any phase's `:auto`
  set."
  (:require [clojure.test :refer [deftest is testing]]
            [moldworks.phase :as phase]))

(deftest ship-molding-run-batch-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in future entries, auto-commits a real robot batch shipment"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/ship-molding-run-batch))
          (str "phase " n " must not auto-commit :actuation/ship-molding-run-batch")))))

(deftest issue-material-certificate-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real Material Certificate of Compliance"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/issue-material-certificate))
          (str "phase " n " must not auto-commit :actuation/issue-material-certificate")))))

(deftest end-of-line-quality-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :end-of-line-quality/screen))
          (str "phase " n " must not auto-commit :end-of-line-quality/screen")))))

(deftest robotics-simulate-clamp-force-test-never-auto-at-any-phase
  (testing "the robot clamp-force-verification mission carries no direct capital risk, but is still never auto-eligible, matching every sibling verification op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :robotics/simulate-clamp-force-test))
          (str "phase " n " must not auto-commit :robotics/simulate-clamp-force-test")))))

(deftest robotics-simulate-clamp-force-test-enabled-from-phase-2
  (is (contains? (:writes (get phase/phases 2)) :robotics/simulate-clamp-force-test))
  (is (contains? (:writes (get phase/phases 3)) :robotics/simulate-clamp-force-test))
  (is (not (contains? (:writes (get phase/phases 1)) :robotics/simulate-clamp-force-test))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":molding-run-batch/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:molding-run-batch/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :molding-run-batch/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/ship-molding-run-batch} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/issue-material-certificate} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :molding-run-batch/intake} :commit)))))
