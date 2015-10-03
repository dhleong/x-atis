(ns ^{:author "Daniel Leong"
      :doc "Configuration UI once a profile is selected"}
  xatis.config
  (:require [clojure.string :refer [capitalize upper-case]]
            [clj-time
             [core :as t]
             [format :as f]]
            [seesaw
             [core :as s]
             [mig :refer [mig-panel]]]
            [asfiled.metar :refer [decode-metar]]
            [xatis
             [render :refer [atis-parts nato]]
             [util :refer [re-replace]]
             [voice :refer [build-part]]]))

;;
;; Constants
;;

(def vrb-wind-format "VRB @ %02d")
(def wind-format "%03d @ %02d")
(def zulu-formatter (f/formatter "HHmm/ss"))

(declare show-config)

;;
;; Util
;;

(defn flag-checkbox
  [flag-key]
  (loop [my-atis-parts atis-parts]
    (if-let [[k v] (first my-atis-parts)]
      (if (and (= flag-key k)
               (string? v))
        (let [label 
              (->> v
                   build-part
                   capitalize
                   (re-replace
                     #"Vfr|atc"
                     upper-case)
                   (str "<html>"))]
          (s/checkbox :id flag-key
                      :text label))
        (recur (next my-atis-parts)))
      (throw (IllegalArgumentException. (str "No ATIS flag " flag-key))))))

(defmacro deftab
  [id label & body]
  `(defn ~id
     []
     {:title ~label
      :content (do ~@body)}))

;;
;; Components
;;

(defn atis-letter-box
  []
  (s/combobox 
    :id :atis-letter
    :model (map (comp str first) nato)))

(defn zulu-time
  []
  (let [widget (s/text
                 :id :time
                 :editable? false
                 :halign :center
                 :text "----/--")
        timer (s/timer
                (fn [_]
                  (s/text! widget 
                           (f/unparse zulu-formatter (t/now))))
                :delay 1000
                :start? true)]
    [widget timer]))


(deftab tab-dep-arr
  "Departure/Arrival"
  (mig-panel
    :constraints ["wrap 3"]
    :items
    [[(s/text :id :dep-arr-notice
              :margin 4
              :multi-line? true
              :wrap-lines? true
              :rows 5)
      "grow,span 4 3,pushy"]
     [(s/checkbox :id :ils-approach
                  :text "ILS approaches in use")
      "grow"]
     [(s/checkbox :id :rnav-approach
                  :text "RNAV approaches in use")
      "grow"]
     [(s/checkbox :id :vor-approach
                  :text "VOR approaches in use")
      "grow"]
     ;
     [(s/checkbox :id :visual-approach
                  :text "Visual approaches in use")
      "grow"]
     [(s/checkbox :id :localize-approach
                  :text "Localizer approaches in use")
      "grow"]
     [(s/checkbox :id :dme-approach
                  :text "DME approaches in use")
      "grow"]]))

(deftab tab-dep-arr-flags
  "Dep/Arr Flags"
  (s/vertical-panel
    :items
    [(flag-checkbox :simul-approach-intersecting)
     (flag-checkbox :converging-ops)
     (flag-checkbox :land-hold-short)
     (flag-checkbox :simul-approachs)
     (flag-checkbox :grass-ops)
     (flag-checkbox :windshear-advs)
     (flag-checkbox :vfr-direction) ]))

(deftab tab-notams
  "NOTAMs"
  (s/text :id :notam
          :margin 4
          :multi-line? true
          :wrap-lines? true
          :rows 5))

(deftab tab-closing-flags
  "Closing Flags"
  (mig-panel
    :constraints ["wrap 1"]
    :items
    [[(flag-checkbox :hold-short-intructions)]
     [(flag-checkbox :readback-assigned-alt)]
     [(s/flow-panel
        :align :left
        :items
        [(s/checkbox :id :deps-ctc-freq
                     :text "All departures contact")
         (s/text :id :ctc-position
                 :columns 8)
         " on "
         (s/text :id :ctc-freq
                 :columns 7)
         " prior to taxi."])
      "pad 0 -5"] ;; necessary for alignment (weird))
     [(flag-checkbox :readback-callsign)]
     [(flag-checkbox :verify-sid)]
     [(flag-checkbox :mode-charlie)]
     [(s/flow-panel
        :align :left
        :items
        [(s/checkbox 
           :id :hazardous-weather
           :text (str "Attention all aircraft, "
                      "hazardous weather info for"))
         (s/text :id :hazardous-weather-area
                 :columns 12) 
         "area"])
      "pad 0 -5"] ;; see above
     ;; super hax to make it look sort-of wrapped
     ["available from ATC by request."
      "pad -45 30"]]))

(deftab tab-config
  "Configuration"
  (mig-panel
    :constraints ["wrap 1"]
    :items
    [[(s/flow-panel
        :align :left
        :items 
        [(s/checkbox 
           :id :normal-update
           :text "Normal METAR Update Time    xx")
         (s/text :id :normal-update-time
                 :columns 2)
         "zulu"])
      "pad 0 -5"] ;; see above
     [(s/flow-panel
        :align :left
        :items 
        [(s/checkbox 
           :id :magnetic-variation
           :text "Magnetic Variation")
         (s/vertical-panel
           :items
           [(s/radio :id :magnetic-subtract
                     :text "Subtract")
            (s/radio :id :magnetic-add
                     :text "Add")])
         (s/text :id :magnetic-degrees
                 :columns 3)
         "Degrees"])
      "pad 0 -5"]
     [(s/checkbox :id :eu-parse
                  :text "Non-US METAR Parse")]]))


;;
;; Primary utils
;;

(defn- pick-profile
  [config]
  (s/input (str "Select " (:id config) " profile")
           :choices (:profiles config)
           :to-string :name))


(defn- show-config-window
  [config profile]
  (let [[zulu-time-widget timer] (zulu-time)
        f (-> (s/frame
                :title (str "xAtis - " (:facility config))
                :on-close :dispose
                :resizable? false
                :content
                (mig-panel
                  :constraints ["wrap 9"]
                  :items
                  ;; top row
                  [[(atis-letter-box) "grow"]
                   [zulu-time-widget "grow,span 2"]
                   [(s/text :id :winds
                            :editable? false
                            :text "--- @ --") "grow,span 3"]
                   [(s/text :id :altimeter
                            :editable? false
                            :text "A----") "grow,span 2"]
                   [(s/button :id :profile-select
                              :text (:name profile)) "grow,span 1"]
                   ;; middle
                   [(s/tabbed-panel
                      :placement :top
                      :overflow :scroll
                      :tabs 
                      [(tab-dep-arr)
                       (tab-dep-arr-flags)
                       (tab-notams)
                       (tab-closing-flags)
                       (tab-config)])
                    "span 9 4,wmax 640"]]))
              s/pack!
              s/show!)]
    ;; ensure timer gets cleaned up
    (s/listen f :window-closed (fn [_] (.stop timer)))
    ;; handle profile swapping
    (s/listen 
      (s/select f [:#profile-select])
      :mouse-clicked
      (fn [e]
        (.dispose e)
        ;; TODO ensure disconnected
        (show-config config)))
    ;; fill fields from profile
    (s/value! f profile)
    (def last-frame f)
    f)) ;; return the frame

;;
;; Public interface
;;

(defn update-weather!
  [root raw-metar]
  (let [metar (decode-metar raw-metar)
        wind (:wind metar)]
    (s/text! (s/select root [:#winds]) 
             (if (= :vrb (:speed wind))
               (format vrb-wind-format (:dir wind))
               (format wind-format (:speed wind) (:dir wind))))))

(defn show-config
  ([config]
   {:pre [(map? config)]}
   (show-config config nil))
  ([config profile]
   {:pre [(map? config)]}
   (if profile
     (show-config-window config profile)
     (recur config (pick-profile config)))))
