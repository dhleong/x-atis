(ns ^{:author "Daniel Leong"
      :doc "Vatsim network implementation"}
  xatis.networks.vatsim
  (:require [clojure.string :refer [lower-case]]
            [seesaw
             [core :as s]
             [mig :refer [mig-panel]]]
            [stubby.core :refer [require-stub]]
            [xatis
             [render :refer [render-atis]]
             [text :refer [build-text]]
             [ui :refer [when-none-empty-set-enabled]]
             [util :refer [split-atis]]
             [network :refer [XAtisNetwork]]]))

(require-stub aileron.core :as a :else xatis.stubs.aileron)

;;
;; Constants
;;

(def text-value-fields [:name :cid :pass])

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

(defn prompt-credentials
  [callback]
  (let [saved-info {}  ;; FIXME
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
                 [(s/text :id :name 
                          :text (:name saved-info)) "grow,w 70::"]
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
                                    params (assoc params-raw
                                                  :rating
                                                  (keyword
                                                    (lower-case 
                                                      (:rating params-raw))))]
                                (swap! connected-status (constantly true))
                                (.dispose (s/to-root %))
                                (callback params))])
                  "grow"]]))
            s/pack!
            s/show!)]
    (when-none-empty-set-enabled
      (s/select f [:#connect])
      text-value-fields)
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
      #(if-let [{:keys [:name :cid :pass :rating :server]} %]
         ;; we got the stuff; connect
         (future
           (a/connect! conn) ;; FIXME the args
           (Thread/sleep 1200) ;; FIXME remove all this
           (on-fail))
         ;; they canceled or something
         (on-fail))))
  (connected?
    [this]
    (a/connected? conn))
  (disconnect!
    [this]
    (a/disconnect! conn))
  (send-to!
    [this cid message]
    (a/send! conn cid message)))

(defn create-network
  [config metar-atom profile-atom atis-letter-factory]
  (let [conn (a/create-connection
               "xAtis v0.1.0"
               0 1
               "xAtis connection")]
    (a/update! conn
               :atis-factory
               #(-> (render-atis 
                      config
                      @profile-atom
                      @metar-atom
                      (atis-letter-factory))
                    build-text
                    split-atis))
    (->VatsimNetwork
      conn
      profile-atom)))
