(ns ^{:author "Daniel Leong"
      :doc "Configuration UI once a profile is selected"}
  xatis.config
  (:require [clojure.string :refer [capitalize upper-case]]
            [clj-time
             [core :as t]
             [format :as f]]
            [seesaw
             [bind :as b]
             [core :as s]
             [mig :refer [mig-panel]]]
            [asfiled.metar :refer [decode-metar]]
            [xatis
             [network :refer :all]
             [render :refer [render-atis atis-parts nato]]
             [subscribe :as subrs]
             [util :refer [re-replace]]
             [tts :refer [preview-speech]]
             [voice :refer [build-part build-voice]]]
            [xatis.networks.vatsim :as vatsim]))

;;
;; Constants
;;

(def wind-format-default "--- @ --")
(def vrb-wind-format "VRB @ %02d")
(def wind-format "%03d @ %02d")
(def zulu-formatter (f/formatter "HHmm/ss"))

(def atis-box-letters (->> nato
                           (map (comp str first))
                           (cons "")))

;; list of keys whose value changes affect the metar
(def valuable-keys
  (conj (map first atis-parts)
        :hazardous-weather-area
        :ctc-position :ctc-freq
        :normal-update :normal-update-time
        :magnetic-variation :magnetic-degrees
        :magnetic-subtract :magnetic-add
        :atis-letter))

(def running-preview (atom nil))

(declare show-config)
(declare update-weather!)

;;
;; Util
;;

(defn- user-data
  [widgetable]
  (-> (s/to-frame widgetable)
      (s/select [:#container])
      s/user-data))

(defn- get-config
  [widgetable]
  (-> (user-data widgetable) :config))

(defn- get-metar
  "Returns the current (raw) METAR in an atom.
  Note that the value may be nil"
  [widgetable]
  (-> (user-data widgetable) :metar))

(defn- get-network
  "Returns the current XAtisNetwork instance"
  [widgetable]
  (-> (user-data widgetable) :network))

(defn- get-profile
  "Returns the profile in an atom"
  [widgetable]
  (-> (user-data widgetable) :profile))

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
    :model atis-box-letters))

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
    :constraints ["wrap 6"]
    :items
    [
     [(s/text :id :dep-arr-notice
              :margin 4
              :multi-line? true
              :wrap-lines? true
              :rows 5)
      "grow,span 6 3,pushy"]
     ;
     [(mig-panel
        :constraints ["wrap 6,insets 0"]
        :items
        [["ARR RWYS:" "grow,span 1"]
         [(s/text :id :arriving-rwys) "grow,pushx,span 2"]
         ["DEP RWYS:" "grow,span 1"]
         [(s/text :id :departing-rwys) "grow,pushx,span 2"]])
      "grow,span 6 1,pad 0"]
     ;
     [(s/checkbox :id :ils-approach
                  :text "ILS approaches in use")
      "grow,pushx,span 2"]
     [(s/checkbox :id :rnav-approach
                  :text "RNAV approaches in use")
      "grow,pushx,span 2"]
     [(s/checkbox :id :vor-approach
                  :text "VOR approaches in use")
      "grow,pushx,span 2"]
     ;
     [(s/checkbox :id :visual-approach
                  :text "Visual approaches in use")
      "grow,pushx,span 2"]
     [(s/checkbox :id :localize-approach
                  :text "Localizer approaches in use")
      "grow,pushx,span 2"]
     [(s/checkbox :id :dme-approach
                  :text "DME approaches in use")
      "grow,pushx,span 2"]
    ]))

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

(defn- safely
  [fun]
  (fn [& args]
    (try
      (apply fun args)
      (catch Exception e
        (def last-safe-exc e)
        nil))))

(defn- handle-metar
  "Bind event handling for METAR updates"
  [f]
  (let [preview-widget (s/select f [:#preview])
        preview-atis-button (s/select f [:#preview-atis])]
    (b/bind
      (s/select f [:#metar])
      (b/transform #(try
                      (decode-metar %)
                      % ;; just making sure we CAN
                      (catch Exception e
                        (def last-metar-exc e)
                        nil)))
      (b/filter (complement nil?))
      (b/tee
        ;; everyone who wants it decoded...
        (b/bind
          (b/transform decode-metar)
          (b/tee
            ;; update the wind
            (b/bind
              (b/transform 
                #(let [wind (:wind %)
                       speed (:speed wind)]
                   (cond 
                     ;; nothing
                     (or (nil? speed)) wind-format-default
                     ;; variable wind
                     (= :vrb (:speed wind))
                     (format vrb-wind-format (:dir wind))
                     ;; normal
                     :else (format wind-format 
                                   (:dir wind)
                                   (:speed wind)))))
              (b/value (s/select f [:#winds])))
            ;; update altimeter
            (b/bind
              (b/transform
                #(str "A" (:altimeter % "----")))
              (b/value (s/select f [:#altimeter])))))
        ;; update the preview
        (b/bind
          (b/transform
            #(try
               (render-atis 
                 (get-config f)
                 @(get-profile f)
                 %
                 (s/value (s/select f [:#atis-letter])))
               (catch Exception e
                 (def last-render-exc e))))
          (b/transform (safely build-voice))
          (b/tee
            (b/value preview-widget)
            #_(b/property preview-atis-button :enabled?)
            (b/b-do
              [atis-text]
              ;; for some reason, b/property is deadlocking
              (def last-foo atis-text)
              (s/invoke-later
                (s/config! preview-atis-button 
                           :enabled? (not (empty? atis-text))))))) 
        (b/b-do 
          [v]
          (swap! (get-metar f) (constantly v)))))))

(defn- handle-values
  [f]
  (doseq [k valuable-keys]
    (if-let [widget (s/select f [(keyword (str "#" (name k)))])]
      (b/bind 
        widget
        (b/transform
          ;; this transform will both update the profile
          ;;  and return it from the atom as a map, for
          ;;  the rest of the chain to use
          #(swap! (get-profile f) assoc k %))
        ;; do nothing if we don't have a metar
        (b/filter (fn [_]
                    (try 
                      (decode-metar @(get-metar f))
                      (catch Exception e
                        (def last-metar-exc e)
                        nil))))
        (b/transform
          #(try
             (render-atis 
               (get-config f)
               %
               @(get-metar f)
               (s/value (s/select f [:#atis-letter])))
             (catch Exception e
               (def last-render-exc e))))
        (b/transform (safely build-voice))
        (b/filter (complement nil?))
        (b/filter (complement empty?))
        (b/value (s/select f [:#preview])))
      (def unfound-widget k))))

(defn- on-new-atis
  "Called when the ATIS has changed. We'll prompt to update the
  ATIS letter."
  [root]
  (let [atis-letter-widget (s/select root [:#atis-letter])
        this-atis (s/value atis-letter-widget)
        this-index (.indexOf atis-box-letters this-atis)
        next-atis (nth atis-box-letters (inc this-index))]
    (when (not= "" this-atis)
      (s/alert root
               (str "New ATIS `" next-atis "` Available")
               :type :info)
      (s/value! atis-letter-widget next-atis))))

(defn- pick-profile
  [config]
  (s/input (str "Select " (:id config) " profile")
           :choices (:profiles config)
           :to-string :name))

(defn toggle-atis-connection
  [e]
  (let [network (get-network e)
        f (s/to-root e)
        btn (s/select f [:#connect-atis])]
    (if (= "Connect ATIS" (s/text btn))
      ;; connect it
      (do
        (config-voice! network 
                       (get-config)
                       (s/value (s/select f [:#preview])))
        (s/text! btn "Disconnect ATIS"))
      ;; disconnect it
      (do
        (config-voice! network nil nil)
        (s/text! btn "Connect ATIS")))))

(defn toggle-atis-preview
  [e]
  (if-let [running @running-preview]
    (swap! running-preview (constantly (.cancel running)))
    (let [preview (s/value (s/select (s/to-frame e) [:#preview]))]
      (when-not (empty? preview)
        (swap! running-preview 
               (constantly
                 (preview-speech preview
                                 #(swap! running-preview 
                                         (constantly nil)))))))))

(defn toggle-connection
  [e]
  (let [network (get-network e)
        button (s/to-widget e)
        new-label (if (connected? network)
                    "Connect" ;; we'll be disconnecting
                    "Disconnect")]
    (s/text! button new-label)
    (if (connected? network)
      (disconnect! network)
      (connect! network 
                (fn [& _]
                  ;; TODO notify error
                  (s/invoke-later
                    (s/text! button "Connect")))))))

(defn- show-config-window
  [config profile]
  (let [metar-atom (atom nil)
        profile-atom (atom profile)
        atis-letter-widget (atis-letter-box)
        subrs (subrs/create-subscriptions)
        network (vatsim/create-network 
                  config metar-atom profile-atom
                  subrs
                  update-weather!
                  #(s/value atis-letter-widget))
        [zulu-time-widget timer] (zulu-time)
        f (-> (s/frame
                :title (str "xAtis - " (:facility config))
                :on-close :dispose
                :resizable? false
                :content
                (mig-panel
                  :id :container
                  :constraints ["wrap 9"]
                  :user-data {:config config
                              :metar metar-atom
                              :network network
                              :profile profile-atom}
                  :items
                  ;; top row
                  [[atis-letter-widget "grow"]
                   [zulu-time-widget "grow,span 2"]
                   [(s/text :id :winds
                            :editable? false
                            :columns 9
                            :halign :center
                            :text wind-format-default) "grow,span 3"]
                   [(s/text :id :altimeter
                            :editable? false
                            :columns 8
                            :halign :center
                            :text "A----") "grow,span 2"]
                   [(s/button :id :profile-select
                              :text (:name profile)) "alignx right,span 1"]
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
                    "span 9 4,wmax 640,hmin 330"]
                   ;; metar line
                   [(s/text :id :metar)
                    "span 9,grow"]
                   ;; voice connection and preview
                   ["Voice Server" "span 3"]
                   [(s/scrollable
                      (s/text :id :preview
                              :editable? false
                              :margin 4
                              :multi-line? true
                              :wrap-lines? true))
                    "span 6 4,grow"]
                   [(s/text :id :server)
                    "span 3,grow"]
                   ["ATIS Frequency" "span 3"]
                   [(s/text :id :frequency)
                    "span 3,grow"]
                   ;; bottom bar
                   ;; for some reason, if these aren't in a 
                   ;;  flow-panel, the whole window stretches
                   [(s/flow-panel
                      :items
                      [(s/button :id :connect-atis
                                 :text "Connect ATIS"
                                 :enabled? false
                                 :listen
                                 [:action toggle-atis-connection])
                       (s/button :id :preview-atis
                                 :text "Preview ATIS"
                                 :enabled? false
                                 :listen 
                                 [:action toggle-atis-preview])])
                    "span 7"]
                   [(s/button :id :connect
                              :text "Connect"
                              :listen
                              [:action toggle-connection])
                    "span 2,alignx right"]
                   ]))
              s/pack!
              s/show!)]
    ;; ensure timer gets cleaned up
    (s/listen f :window-closed (fn [_] (.stop timer)))
    ;; handle profile swapping
    (s/listen 
      (s/select f [:#profile-select])
      :mouse-clicked
      (fn [e]
        (.dispose f)
        ;; TODO ensure disconnected
        (show-config config)))
    ;; fill fields from profile/config
    (s/value! f profile)
    (s/value! f config)
    ;; when the metar changes, do all the things
    (handle-metar f)
    (handle-values f)
    ;; when the atis-letter changes, notify subscribers
    (b/bind
      atis-letter-widget
      (b/b-do
        [v]
        (when (not (empty? v))
          (subrs/each-sub subrs
            (notify-atis! (get-network f) sub v)))))
    (def last-frame f)
    f)) ;; return the frame

;;
;; Public interface
;;

(defn update-weather!
  [root raw-metar]
  ;; the bindings will handle everything
  (let [widget (s/select root [:#metar])
        preview-widget (s/select root [:#preview])
        safely-build-voice (safely build-voice)
        old-atis (s/text preview-widget)
        new-atis (-> (render-atis 
                        (get-config root)
                        @(get-profile root)
                        raw-metar
                        (s/value (s/select root [:#atis-letter])))
                     safely-build-voice)]
    ;; if the atis changed, wait to update 
    ;;  until we've ack'd it
    (when (not= old-atis new-atis)
      (on-new-atis root))
    (s/text! widget raw-metar)
    ;; this widget doesn't seem to get updated
    (s/repaint! widget)))

(defn show-config
  ([config]
   {:pre [(map? config)]}
   (show-config config nil))
  ([config profile]
   {:pre [(map? config)]}
   (if profile
     (show-config-window config profile)
     (recur config (pick-profile config)))))
