(ns ^{:author "Daniel Leong"
      :doc "Vatsim network implementation"}
  xatis.networks.vatsim
  (:require [clojure
             [edn :as edn]
             [string :refer [join lower-case split]]]
            [clojure.java.io :refer [file] :as io]
            [seesaw
             [core :as s]
             [mig :refer [mig-panel]]]
            [asfiled.skyvector :refer [get-vor]]
            [stubby.core :refer [require-stub]]
            [xatis
             [render :refer [render-atis]]
             [subscribe :as subrs]
             [text :refer [build-text]]
             [ui :refer [when-none-empty-set-enabled]]
             [util :refer [resolve-file split-atis]]
             [network :refer [XAtisNetwork]]]))

(require-stub aileron.core :as a :else xatis.stubs.aileron)
(require-stub xatis.networks.vatsim-creds 
              :as vc 
              :else xatis.stubs.vatsim-creds)

;;
;; Constants
;;

; major, minor, tertiary, for laziness
(def version [0 1 0])

(def windows? (-> (System/getProperty "os.name")
                  (.toLowerCase)
                  (.contains "windows")))

(def vatsim-port 6809)

(def default-settings-file
  (resolve-file
    (if windows?
     "~/xradar.settings.edn"
     "~/.xradar.settings.edn")))

(def text-value-fields [:full-name :cid :pass])
(def vatsim-keys [:full-name :cid :pass ])

(def notify-atis-fmt "New Atis! Information %s is current.")
(def notify-subs-fmt "You are now subscribed to ATIS changes.")
(def notify-unsub-fmt "You are no longer subscribed.")

(def ratings ["Observer"
              "Student1"
              "Student2"
              "Student3"
              "Controller1"
              "Controller2"
              "Controller3"
              "Instructor1"
              "Instructor2"
              "Instructor3"
              "Supervisor"
              "Administrator"])


;;
;; Internal util
;;

(def param-to-key
  (comp keyword lower-case))

(defn on-message
  [subrs msg send-msg!]
  (let [input (lower-case (:text msg))
        callsign (:from msg)]
    (condp #(= 0 (.indexOf %2 %1)) input
      "sub" (do
              (subrs/subscribe subrs callsign)
              (send-msg! callsign notify-subs-fmt))
      "unsub" (do
                (subrs/unsubscribe subrs callsign)
                (send-msg! callsign notify-unsub-fmt))
      nil))) ;; some other message (ignore)

(defn- read-binding
  [tag value]
  (let [parts (split (str tag) #"/")
        command (first parts)]
    (when (= "set" command)
      (let [[_ setting-name] parts
            setting-key (keyword setting-name)]
        (when (= :connections setting-key)
          (let [info (first value)]
            (assoc info
                   :full-name (:name info))))))))

(defn load-saved-info
  []
  (let [f (file default-settings-file)]
    (if (.exists f)
      (with-open [reader (java.io.PushbackReader. (io/reader f))]
        (loop [result {}]
          (let [read-val (edn/read {:default read-binding
                                    :eof :eof} 
                                   reader)]
            (cond
              (= :eof read-val) result
              (not (nil? read-val)) read-val
              :else (recur result)))))
      {})))

(defn prompt-credentials
  [callback]
  (let [saved-info (load-saved-info)
        connected-status (atom false)
        f
        (-> (s/frame
              :title "Connect to Vatsim"
              :on-close :dispose
              :resizable? false
              :content
              (mig-panel
                :id :container
                :constraints ["wrap 2"]
                :items 
                [["Full Name:" "right"]
                 [(s/text :id :full-name 
                          :text (:full-name saved-info)) "grow,w 70::"]
                 ["Rating:" "right"]
                 [(s/combobox :id :rating
                              :model ratings)]
                 ["Certificate ID" "right"]
                 [(s/text :id :cid
                          :text (:cid saved-info)) "grow,w 70::"]
                 ["Password:" "right"]
                 [(s/password :id :pass 
                              :text (:pass saved-info)) "grow,w 70::"]
                 ["Server:" "right"]
                 [(s/combobox :id :server
                              :model ["USA-E"])] ;; FIXME TODO
                 ;; bottom buttons
                 [(s/button :text "Cancel"
                            :listen
                            [:action #(.dispose (s/to-root %))])]
                 [(s/button :id :connect
                            :text "Connect"
                            :enabled? false
                            :listen
                            [:action
                             #(let [params-raw (s/value (s/to-root %))
                                    params (-> params-raw
                                               (select-keys
                                                 vatsim-keys)
                                               (assoc 
                                                 :rating
                                                 (param-to-key 
                                                   (:rating params-raw))))]
                                (swap! connected-status (constantly true))
                                (.dispose (s/to-root %))
                                (callback params))])
                  "grow"]]))
            s/pack!
            s/show!)]
    (when-let [rating (:rating saved-info)]
      (s/selection! (s/select f [:#rating]) rating))
    (when-none-empty-set-enabled
      (s/select f [:#connect])
      text-value-fields)
    ;; manually re-set these so the listener is updated
    (s/value! (s/select f [:#full-name]) (:full-name saved-info))
    (s/value! (s/select f [:#cid]) (:cid saved-info))
    (s/value! (s/select f [:#pass]) (:pass saved-info))
    (s/listen 
      f :window-closed (fn [_]
                        (when-not @connected-status
                          (callback nil))))
    f))

;;
;; Implementation and factory
;;

(deftype VatsimNetwork [conn profile-atom]
  XAtisNetwork
  (config-voice!
    [this config voice-atis]
    ;; TODO
    )
  (connect!
    [this on-fail]
    (prompt-credentials
      (fn [config]
        (if (not (nil? config))
          ;; we got the stuff; connect
          (do 
            (doseq [[k v] config]
              (a/update! conn k v))
            (future
              (try
                (println "Connecting...")
                ;; (a/connect! conn (:server config) vatsim-port)
                (a/connect! conn "fsd.dev.vatsim.net" vatsim-port)
                (println "Connected!")
                ;; NB if we get here, we should be connected
                ;; TODO request metar for the facility
                ;;  when we're ready
                ;; (a/request-metar conn (:id config))
                (catch Throwable e
                  (println "Unable to connect" e)
                  (on-fail)
                  (s/alert (str "Unable to connect:\n"
                                (.getMessage e))
                           :type :error)))))
          ;; they canceled or something
          (on-fail)))))
  (connected?
    [this]
    (a/connected? conn))
  (disconnect!
    [this]
    (a/disconnect! conn)
    (println "Disconnected."))
  (notify-atis!
    [this cid atis-letter]
    (a/send! conn cid (format notify-atis-fmt atis-letter)))
  (send-to!
    [this cid message]
    (a/send! conn cid message)))

(defn create-network
  [config metar-atom profile-atom subrs
   on-metar atis-letter-factory]
  (let [conn (a/create-connection
               ; app name:
               (str "xAtis - " (join "." version))
               ; major, minor version:
               (first version) (second version)
               ; client creds:
               (vc/client-id) (vc/client-key)
               :atc ;; client-type
               "xAtis connection")
        callsign (str (:id config) "_ATIS")]
    (a/update! conn
               :atis-factory
               #(-> (render-atis 
                      config
                      @profile-atom
                      @metar-atom
                      (atis-letter-factory))
                    build-text
                    split-atis))
    ; set preliminary coords (we try to fetch them later)
    (a/update! conn :lat 0)
    (a/update! conn :lon 0)
    (a/update! conn :alt 0)
    (a/update! conn :vis-range 0)
    (a/update! conn :facility :tower)
    (a/update! conn :callsign callsign)
    (a/update! conn :freq (:frequency config))
    ;; try to get the actual lat/lon of 
    ;;  the facility asynchronoulsy
    (future
      (when-let [info (get-vor "KLGA" (:id config))]
        (def found-airport info)
        (when-let [lat (:lat info)]
          (a/update! conn :lat (Double/parseDouble lat)))
        (when-let [lon (:lon info)]
          (a/update! conn :lon (Double/parseDouble lon)))))
    ;; listen for metars, and request them
    (a/listen conn :metars on-metar)
    ;; (a/request-metar conn (:id config))
    ;; listen for subscription messages
    (a/listen conn
              :messages
              #(on-message subrs %
                           (partial a/send! conn)))
    (a/listen conn
              :errors
              #(do
                 (println "ERROR: " %)
                 (s/alert % :type :error)))
    (->VatsimNetwork
      conn
      profile-atom)))
