(ns moldworks.moldworksadvisor
  "Molding Advisor client -- the *contained intelligence node* for the
  injection-molding-plant manufacturing actor.

  It normalizes molding-run-batch intake, drafts a per-product-class
  material-spec evidence checklist, screens batches for an unresolved
  end-of-line-detected defect, drafts the batch-shipment action, and
  drafts the Material Certificate of Compliance issuance action.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record or a real robot dispatch/certificate issuance.
  Every output is censored downstream by `moldworks.governor` before
  anything touches the SSoT, and `:actuation/ship-molding-run-batch`/
  `:actuation/issue-material-certificate` proposals NEVER auto-commit
  at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/ship-molding-run-batch | :actuation/issue-material-certificate | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [moldworks.facts :as facts]
            [moldworks.registry :as registry]
            [moldworks.robotics :as robotics]
            [moldworks.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the batch, the shrinkage-rate figures or the
  product-class scheme. High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "成形バッチ記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :molding-run-batch/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-requirements
  "Per-product-class material-spec evidence checklist draft.
  `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a product class with NO official
  spec-basis in `moldworks.facts` -- the Clamp-Force Governor must
  reject this (never invent a product class's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [a (store/molding-run-batch db subject)
        scheme (if no-spec? "MEDDEV" (:jurisdiction a))
        sb (facts/spec-basis scheme)]
    (if (nil? sb)
      {:summary    (str scheme " の公式spec-basisが見つかりません")
       :rationale  "moldworks.facts に未登録の製品区分。要件を推測で作らない。"
       :cites      []
       :effect     :material-spec-verification/set
       :value      {:jurisdiction scheme :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str scheme " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :material-spec-verification/set
       :value      {:jurisdiction scheme
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-eol-defect
  "End-of-line-defect (short-shot/flash/warpage) screening draft.
  `:molding-run-batch-defect-unresolved?` on the batch record injects
  the failure mode: the Clamp-Force Governor must HOLD, un-
  overridably, on any unresolved defect."
  [db {:keys [subject]}]
  (let [a (store/molding-run-batch db subject)]
    (cond
      (nil? a)
      {:summary "対象成形バッチ記録が見つかりません" :rationale "no batch record"
       :cites [] :effect :eol-screen/set :value {:batch-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:molding-run-batch-defect-unresolved? a))
      {:summary    (str (:batch-name a) ": 未解決の完成検査欠陥(ショートショット/バリ/反り)を検出")
       :rationale  "完成検査スクリーニングが未解決の欠陥を検出。人手確認とホールドが必須。"
       :cites      [:eol-check]
       :effect     :eol-screen/set
       :value      {:batch-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:batch-name a) ": 未解決の完成検査欠陥なし")
       :rationale  "完成検査欠陥スクリーニング完了。"
       :cites      [:eol-check]
       :effect     :eol-screen/set
       :value      {:batch-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- simulate-clamp-force-test
  "Runs the robot clamp-force-verification mission
  (`moldworks.robotics`) and drafts its result as a proposal. High
  confidence -- the mission itself is a REAL time-stepped `physics-2d`
  rigid-body simulation derived from the batch's own recorded press-
  run configuration, not an LLM guess; the Clamp-Force Governor still
  independently re-derives :passed? from those same fields before any
  `:actuation/ship-molding-run-batch` proposal may commit -- see
  `moldworks.governor`'s `robotics-simulation-violations`."
  [db {:keys [subject]}]
  (let [a (store/molding-run-batch db subject)]
    (if (nil? a)
      {:summary "対象成形バッチ記録が見つかりません" :rationale "no batch record"
       :cites [] :effect :molding-run-batch/upsert :value {:id subject :robotics-sim-verified? false}
       :stake nil :confidence 0.0}
      (let [{:keys [mission actions passed? sim-peak-clamp-force-n sim-peak-clamp-tonnage]}
            (robotics/simulate-clamp-force-test subject a)]
        {:summary    (str subject ": クランプ力検証ロボットミッション " (if passed? "合格" "不合格")
                          " (実測 " sim-peak-clamp-tonnage " トン)")
         :rationale  (str "mission=" (:mission/id mission) " actions=" (count actions)
                          " sim-peak-clamp-force-n=" sim-peak-clamp-force-n)
         :cites      [(:mission/id mission)]
         :effect     :molding-run-batch/upsert
         :value      {:id subject
                      :robotics-sim-verified? passed?
                      :robotics-sim-record {:mission-id (:mission/id mission)
                                            :actions (mapv #(dissoc % :action) actions)
                                            :passed? passed?}}
         :stake      nil
         :confidence 0.95}))))

(defn- propose-molding-run-batch-shipment
  "Draft the actual MOLDING-RUN-BATCH-SHIPMENT action -- dispatching a
  real robot handling/shipment action on a safety-critical batch.
  ALWAYS `:stake :actuation/ship-molding-run-batch` -- this is a
  REAL-WORLD safety-critical act, never a draft the actor may
  auto-run. See README `Actuation`: no phase ever adds this op to a
  phase's `:auto` set (`moldworks.phase`); the governor also always
  escalates on `:actuation/ship-molding-run-batch`. Two independent
  layers agree, deliberately."
  [db {:keys [subject]}]
  (let [a (store/molding-run-batch db subject)]
    {:summary    (str subject " 向け成形バッチ出荷提案"
                      (when a (str " (batch=" (:batch-name a) ")")))
     :rationale  (if a
                   (str "shrinkage-rate-deviation-actual-pct=" (:shrinkage-rate-deviation-actual-pct a)
                        " spec=[" (:shrinkage-rate-deviation-min-pct a) "," (:shrinkage-rate-deviation-max-pct a) "]")
                   "成形バッチ記録が見つかりません")
     :cites      (if a [subject] [])
     :effect     :molding-run-batch/mark-shipped
     :value      {:batch-id subject}
     :stake      :actuation/ship-molding-run-batch
     :confidence (if (and a (not (registry/molding-run-batch-shrinkage-out-of-range? a))) 0.9 0.3)}))

(defn- propose-material-certificate
  "Draft the actual MATERIAL CERTIFICATE OF COMPLIANCE action --
  issuing a real Material Certificate of Compliance certifying a
  batch's material-spec conformance. ALWAYS `:stake :actuation/issue-
  material-certificate` -- this is a REAL-WORLD safety-critical act,
  never a draft the actor may auto-run. See README `Actuation`: no
  phase ever adds this op to a phase's `:auto` set (`moldworks.
  phase`); the governor also always escalates on `:actuation/issue-
  material-certificate`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [a (store/molding-run-batch db subject)]
    {:summary    (str subject " 向け材料証明書発行提案"
                      (when a (str " (batch=" (:batch-name a) ")")))
     :rationale  (if a
                   "material-spec-evidence-checklist referenced"
                   "成形バッチ記録が見つかりません")
     :cites      (if a [subject] [])
     :effect     :molding-run-batch/mark-certified
     :value      {:batch-id subject}
     :stake      :actuation/issue-material-certificate
     :confidence (if a 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :molding-run-batch/intake                    (normalize-intake db request)
    :material-spec-rules/verify                  (verify-requirements db request)
    :end-of-line-quality/screen                  (screen-eol-defect db request)
    :robotics/simulate-clamp-force-test          (simulate-clamp-force-test db request)
    :actuation/ship-molding-run-batch            (propose-molding-run-batch-shipment db request)
    :actuation/issue-material-certificate        (propose-material-certificate db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは射出成形プラントの出荷実行・材料証明書発行エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:molding-run-batch/upsert|:material-spec-verification/set|:eol-screen/set|"
       ":molding-run-batch/mark-shipped|:molding-run-batch/mark-certified) "
       "(:robotics/simulate-clamp-force-test も :molding-run-batch/upsert で "
       ":robotics-sim-verified? を提案する) "
       ":stake(:actuation/ship-molding-run-batch か :actuation/issue-material-certificate か nil) :confidence(0..1)。\n"
       "重要: 登録されていない製品区分の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [subject]}]
  {:batch (store/molding-run-batch st subject)})

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Clamp-Force Governor
  escalates/holds -- an LLM hiccup can never auto-ship a molding-run-
  batch or auto-issue a Material Certificate of Compliance."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :moldworksadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
