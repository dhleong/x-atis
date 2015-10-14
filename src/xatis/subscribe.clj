(ns ^{:author "Daniel Leong"
      :doc "Handle update subscribers"}
  xatis.subscribe
  (:require [clojure.string :as s]))

;; (defn notify!
;;   [subscriptions new-atis-letter]
;;   (let [sub @subscriptions
;;         on-update (:on-update sub)]
;;     (doseq [[callsign _] sub]
;;       (when (not= :on-update callsign)
;;         (on-update callsign new-atis-letter)))))

(defmacro each-sub
  "Given a subscriptions object, executes body
  for each subscriber, with the callsign bound as `sub`"
  [subscriptions & body]
  `(doseq [[~'sub _#] (deref ~subscriptions)]
     ~@body))

(defn subscribe
  [subscriptions callsign]
  (swap! subscriptions assoc callsign true))

(defn unsubscribe
  [subscriptions callsign]
  (swap! subscriptions dissoc callsign true))

(defn create-subscriptions
  "Create a subscriptions object. 
  `on-update` will be called as (on-update callsign letter)
  whenever the letter changes"
  []
  (atom {}))
