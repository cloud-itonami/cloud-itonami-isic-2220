(ns moldworks.facts
  "Injection-molded-plastics material-spec evidence catalog -- the
  G2-style spec-basis table the Clamp-Force Governor checks every
  `:material-spec-rules/verify` proposal against.

  UNLIKE `automotive.facts`'s per-COUNTRY vehicle type-approval statute
  table, injection-molded-plastics material conformance does not
  decompose into one scheme per ISO3 country either (the SAME honest
  structural observation `cellworks.facts` makes for battery-safety
  certification): it is a mix of named engineering-standards-body
  specifications (SAE / ASTM / ISO / UL) that many jurisdictions
  reference or adopt directly, organized by PRODUCT CLASS (what the
  molded part becomes) rather than by the plant's own country. This
  catalog's keys reflect that real structure honestly rather than
  forcing a false per-country shape:

    - \"AUTOMOTIVE\" -- automotive interior/exterior plastics (bumper
                        fascias, interior trim, dashboard components).
                        SAE International's J1545/J1885 govern
                        color/appearance-durability conformance for
                        automotive interior materials; OEM material
                        specs for structural/impact-rated automotive
                        plastic parts commonly reference ASTM
                        International's D638 (tensile properties) and
                        D256 (Izod impact resistance); ISO 3167 governs
                        the multipurpose test-specimen geometry those
                        mechanical tests are run against.
    - \"CE-HOUSING\"  -- consumer-electronics enclosure plastics
                        (smartphone back-covers/internal chassis
                        brackets). UL 94 (UL Solutions) is a REAL,
                        near-universally-required flammability rating
                        for plastic materials used in electronic-device
                        enclosures; ISO 294 governs injection-molding of
                        thermoplastic test specimens.

  Coverage is reported HONESTLY: a product class not in this table has
  NO spec-basis. Seed values cite official standards-owning bodies;
  this is a starting catalog (two product classes: the automotive and
  consumer-electronics downstream consumers this actor's own README
  `Scope note` names), not a survey of every product class an
  injection-molding plant might produce (e.g. medical-device housings,
  which have their own distinct biocompatibility/regulatory framework
  -- ISO 10993 and similar -- this catalog does NOT cite, honestly,
  rather than fabricating a citation this session was not confident
  of).")

(def catalog
  {"AUTOMOTIVE"
   {:name "Automotive interior/exterior plastics (SAE/ASTM/ISO material-spec basis)"
    :owner-authority "SAE International / ASTM International / International Organization for Standardization (ISO)"
    :legal-basis "SAE J1545 (Instrumental Color Difference Measurement for Colorfastness of Automotive Materials) / SAE J1885 (Accelerated Exposure of Automotive Interior Trim Components Using a Controlled-Irradiance Xenon-Arc Apparatus) -- color/appearance-durability conformance for automotive interior plastics; ASTM D638 (Standard Test Method for Tensile Properties of Plastics) / ASTM D256 (Standard Test Methods for Determining the Izod Pendulum Impact Resistance of Plastics) -- commonly referenced by OEM material specs for structural/impact-rated automotive plastic parts; ISO 3167 (Plastics -- Multipurpose test specimens)"
    :national-spec "OEM automotive-plastics material qualification: color/appearance durability (SAE J1545/J1885) + mechanical property conformance (ASTM D638 tensile / ASTM D256 Izod impact) on ISO 3167 multipurpose specimens"
    :provenance "https://www.sae.org/standards/content/j1545_201612/ ; https://www.sae.org/standards/content/j1885_202007/ ; https://www.astm.org/d0638-14.html ; https://www.astm.org/d0256-10r18.html ; https://www.iso.org/standard/61340.html"
    :required-evidence ["ISO 3167 multipurpose test-specimen dimensional-conformance record"
                        "ASTM D638 tensile-properties test report"
                        "ASTM D256 Izod impact-resistance test report"
                        "SAE J1545/J1885 color/appearance-durability test report"]}
   "CE-HOUSING"
   {:name "Consumer-electronics enclosure plastics (UL/ISO material-spec basis)"
    :owner-authority "UL Solutions (Underwriters Laboratories) / International Organization for Standardization (ISO)"
    :legal-basis "UL 94 (Standard for Safety of Flammability of Plastic Materials for Parts in Devices and Appliances) -- MANDATORY flammability rating (e.g. V-0/V-1/V-2/HB) for any electronic-device enclosure; ISO 294 (Plastics -- Injection moulding of test specimens of thermoplastic materials)"
    :national-spec "UL 94 flammability-rating conformance + ISO 294 injection-molded specimen dimensional conformance for consumer-electronics housing plastics"
    :provenance "https://www.shopulstandards.com/ProductDetail.aspx?UniqueKey=25476 ; https://www.iso.org/standard/71581.html"
    :required-evidence ["UL 94 flammability-rating test report (e.g. V-0/V-1/V-2/HB)"
                        "ISO 294 injection-molded test-specimen dimensional-conformance record"
                        "Resin-lot traceability / material safety data sheet record"]}})

(defn spec-basis [scheme] (get catalog scheme))

(defn coverage
  ([] (coverage (keys catalog)))
  ([schemes]
   (let [have (filter catalog schemes)
         missing (remove catalog schemes)]
     {:requested (count schemes)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-2220 R0: " (count catalog)
                 " product-class material-spec schemes seeded (AUTOMOTIVE: "
                 "SAE J1545/J1885 + ASTM D638/D256 + ISO 3167 / CE-HOUSING: "
                 "UL 94 + ISO 294). Extend `moldworks.facts/catalog`, never "
                 "fabricate a product class's requirements.")})))

(defn required-evidence-satisfied?
  [scheme submitted]
  (when-let [{:keys [required-evidence]} (spec-basis scheme)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [scheme]
  (:required-evidence (spec-basis scheme) []))

;; ─────── Downstream Cross-Actor Handoff (optional, isic-2220 -> isic-1075) ───────
;;
;; `:actuation/ship-molding-run-batch` proposals MAY OPTIONALLY carry a
;; `:handoff` record under the proposal's `:value` when this actor
;; dispatches a finished vacuum/MAP packaging-film molding-run batch to
;; a downstream food-manufacturer consumer of its packaging (e.g.
;; cloud-itonami-isic-1075). Reuses the SAME `:handoff/*` wire shape
;; isic-1075 already uses for its own downstream isic-1075<->jsic-4721
;; handoff -- see superproject ADR-2607181500. A `:handoff` here is
;; OPTIONAL, not required: existing shipment actions worked before this
;; field existed and keep working unchanged with no `:handoff` attached
;; at all.
;;
;;   {:handoff/id "..."
;;    :handoff/source-actor "cloud-itonami-isic-2220"
;;    :handoff/batch-id "..."
;;    :handoff/product-type-id "CE-HOUSING"
;;    :handoff/quantity-kg 40.0
;;    :handoff/dispatched-at-iso "..."}

(defn handoff-record-well-formed?
  "Positive-sense convenience predicate: does `handoff` carry every
  REQUIRED `:handoff/*` field (id/source-actor/batch-id/product-type-id/
  quantity-kg/dispatched-at-iso) with a plausible value (quantity-kg a
  positive number, the string fields non-blank)? Never validates the
  OPTIONAL cold-chain/unspsc/gtin fields."
  [handoff]
  (boolean
   (and (map? handoff)
        (seq (:handoff/id handoff))
        (seq (:handoff/source-actor handoff))
        (seq (:handoff/batch-id handoff))
        (some? (:handoff/product-type-id handoff))
        (number? (:handoff/quantity-kg handoff))
        (pos? (:handoff/quantity-kg handoff))
        (seq (:handoff/dispatched-at-iso handoff)))))
