(ns ^{:author "Daniel Leong"
      :doc "Render an ATIS profile to text"}
  xatis.render
  (:require [clojure.string :refer [join split upper-case] :as s]
            [clj-time.core :as t]
            [asfiled.metar :refer [decode-metar]]))

(def nato
  {\A "Alpha"   \B "Bravo"    \C "Charlie" \D "Delta"
   \E "Echo"    \F "Foxtrot"  \G "Golf"    \H "Hotel"
   \I "India"   \J "Juliett"  \K "Kilo"    \L "Limo"
   \M "Mike"    \N "November" \O "Oscar"   \P "Papa"
   \Q "Quebec"  \R "Romeo"    \S "Sierra"  \T "Tango"
   \U "Uniform" \V "Victor"   \W "Whiskey" \X "X-Ray"
   \Y "Yankee"  \Z "Zulu"})

(defn read-number
  "Convert a number into a vector of digits"
  [number & [min-length]]
  ;; it would be more efficient to strip them
  ;;  off with maths, but... laziness
  (let [fmt (if min-length
              (str "%0" min-length "d")
              "%s")
        the-num (if (string? number)
                  (Integer/parseInt number)
                  number)]
    (->> (int the-num) ;; make sure it's a number
         (format fmt)
         (map #(- (int %) (int \0))))))

(defn render-runways
  [label config]
  (let [runways (split config #"[, :|;]+")
        rwy-or-rwys (if (= 1 (count runways))
               "RWY"
               "RWYS")
        last-rwy (last runways)]
    (str 
      (join
        (cons
          (str label " " rwy-or-rwys " ") 
          (rest ;; drop the redundant first separator
                (mapcat
                  (fn [rwy]
                    (let [n (s/replace rwy #"[^0-9]" "")
                          extra (.replace rwy n "")
                          sep (if (= rwy last-rwy)
                                ", AND "
                                ", ")]
                      (if (.isEmpty extra)
                        [sep (join (read-number n))]
                        [sep (join (read-number n)) extra])))
                  runways))))
      ".")))

(def atis-parts
  (partition
    2
    [:ils-approach #(if (get % :visual-approach)
                      "VISUAL, AND ILS APPROACHES IN USE."
                      "ILS APPROACHES IN USE.")
     :visual-approach #(when-not (get % :ils-approach)
                         ;; ils got it
                         "VISUAL APPROACHES IN USE.")
     ;; TODO in fact, there's also RNAV, VOR, Localizer, and DME,
     ;;  and they probably all should combine together
     :dep-arr-notice identity
     :arriving-rwys #(render-runways "LDG" (:arriving-rwys %))
     :departing-rwys #(render-runways "DEPTG" (:departing-rwys %))
     :simul-approach-intersecting 
     "SIMUL APCHS TO PARALLEL AND INTERSECTING RWYS ARE IN USE."
     :converging-ops "CONVERGING RWY OPS IN EFFECT."
     :land-hold-short "LAND AND HOLD SHORT OPERATIONS ARE IN EFFECT."
     :simul-approachs "SIMUL APCHS ARE IN USE."
     :grass-ops "GRASS RWY OPS IN EFFECT." ;; I guess?
     :windshear-advs "LLWS ADVZYS IN EFFECT." ;; I guess?
     :vfr-direction "VFR ACFT SAY DRCTN OF FLT."
     :notam #(vec ["NOTAMS" (:notam %)])
     :hazardous-weather #(str "ATTN ALL ACFT, HAZ WX INFO FOR "
                              (:hazardous-weather-area %)
                              " AREA AVBL FROM ATC BY REQUEST.")
     :hold-short-intructions "READBACK ALL RWY HS INSTRUCTIONS."
     :readback-assigned-alt 
     "READBACK ALL RWY HS INSTRUCTIONS AND ASSIGNED ALTS."
     :deps-ctc-freq #(str "ALL DEPS CTC "
                          (:ctc-position %)
                          " ON "
                          (:ctc-freq %)
                          " PRIOR TO TAXI.")
     :readback-callsign 
     (str "UPON RECEIPT OF YOUR ATC CLNC READBACK ONLY YOUR CALLSIGN "
          "AND XPNDR CODE UNLESS YOU HAVE A QUESTION.") ;; I guess?
     :verify-sid 
     "VFY YOUR ASSIGNED SID WITH CLNC DELIVERY WHEN READY TO PUSH AND TAXI."
     :mode-charlie "OPER XPNDR ON MODE CHARLIE ON ALL TWYS AND RWYS."]))

(defn render-atis
  "Renders the atis to a vector of parts, from which
  the text-atis and voice-atis can be more easily generated.
  - Strings should generally be read literally, after 
  replacing abbreviations.
  - Chars should be read as their nato-phonetic letter
  - Numbers should be read out as the normal, english reading.
  - Vectors of numbers should be read out individually, and
  rendered to text separated by whitespace
  `metar` should be the raw METAR"
  [config profile metar information-letter]
  (let [letter (first (upper-case information-letter))
        decoded (decode-metar metar)
        nato-letter (get nato letter letter)
        mag-add? (:magnetic-add profile)
        mag-subtract? (:magnetic-subtract profile)
        mag-degrees (try 
                      (Integer/parseInt
                        (:magnetic-degrees profile))
                      (catch Exception e
                        0))
        zulu (:time metar)]
    {:meta
     (assoc
       config
       :info nato-letter
       :magnetic-add (cond
                       mag-add? mag-degrees
                       mag-subtract? (- mag-degrees)
                       :else 0)
       :magnetic-variation (:magnetic-variation profile))
     :metar (subs metar (inc (.indexOf metar " " 5)))
     :weather decoded
     :parts
     (->> atis-parts
          (mapcat
            (fn [[k v]] 
              (let [prof-val (get profile k)]
                (when (and prof-val
                           (or (not (string? prof-val))
                               (not (empty? prof-val))))
                  (let [parsed 
                        (cond
                          (string? v) v
                          (= identity v) (k profile)
                          :else (v profile))]
                    (if (and (seq parsed)
                             (not (string? parsed)))
                      parsed
                      [parsed]))))))
          (filter identity))}))
