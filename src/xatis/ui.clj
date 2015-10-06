(ns ^{:author "Daniel Leong"
      :doc "Common UI utils"}
  xatis.ui
  (:require [seesaw
             [bind :as b]
             [core :as s]]))

;; borrowed from xRadar

(defn when-all-set-enabled
  "When all views with provided ids match
  a given predicate, set the target widget enabled"
  [w pred ids]
  (let [frame (s/to-root w)]
    (b/bind 
      (apply b/funnel 
             (->> ids
                  (map 
                    #(keyword (str "#" (name %))))
                  (map
                    #(s/select frame [%]))))
      (b/transform #(every? pred %))
      (b/property w :enabled?))))

(defn when-none-empty-set-enabled
  "Shortcut to perform when-all-set-enabled
  with the predicate (complement empty?)"
  [w ids]
  (when-all-set-enabled w (complement empty?) ids))

