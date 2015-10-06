(ns ^{:author "Daniel Leong"
      :doc "Vatsim network implementation"}
  xatis.networks.vatsim
  (:require [stubby.core :refer [require-stub]]
            [xatis
             [render :refer [render-atis]]
             [text :refer [build-text]]
             [util :refer [split-atis]]
             [network :refer [XAtisNetwork]]]))

(require-stub aileron.core :as a :else xatis.stubs.aileron)

(deftype VatsimNetwork [conn profile-atom]
  XAtisNetwork
  (config-voice!
    [this config voice-atis]
    ;; TODO
    )
  (connect!
    [this params]
    ;; TODO connect!
    )
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
    (-> VatsimNetwork
        conn
        profile-atom)))
