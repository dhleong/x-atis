(ns ^{:author "Daniel Leong"
      :doc "Configuration UI once a profile is selected"}
  xatis.config
  (:require [clj-time
             [core :as t]
             [format :as f]]
            [seesaw
             [core :as s]
             [mig :refer [mig-panel]]]
            [asfiled.metar :refer [decode-metar]]
            [xatis.render :refer [nato]]))

;;
;; Constants
;;

(def vrb-wind-format "VRB @ %02d")
(def wind-format "%03d @ %02d")
(def zulu-formatter (f/formatter "HHmm/ss"))

;;
;; Util
;;

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
              :multi-line? true
              :wrap-lines? true
              :rows 3)
      "grow,span 4 3"]
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
                      [(tab-dep-arr)])
                    "grow,span 9 4"]]))
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
