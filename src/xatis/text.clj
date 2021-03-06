(ns ^{:author "Daniel Leong"
      :doc "Take a rendered ATIS object and build a Text ATIS"}
  xatis.text
  (:require [clojure.string :refer [join split upper-case] :as s]
            [clj-time.core :as t]
            [xatis
             [render :refer [read-number]]
             [util :refer [typed-dispatch-fn]]
             [voice :refer [render-numbers]] ]))

(defn expand-airports-navaids
  [text]
  (-> text
      (s/replace
        #"[@*]([A-Z]{3,4}|[A-Z]{3})\b"
        "$1")))

(defn expand-taxiways
  [text]
  (-> text
      (s/replace 
        #"%(([A-Z]{1,2})([0-9]*))\b" 
        "$1")))

(defn expand-numbers
  [text]
  (-> text
      (s/replace
        #"#([0-9]+)\b" 
        "$1")))

(defmulti build-part typed-dispatch-fn)
(defmethod build-part :seq
  [part]
  (join part))
(defmethod build-part :default
  [part]
  (-> part
      expand-taxiways
      expand-airports-navaids
      (s/replace #"^NOTAMS" "NOTICES TO AIRMEN;")))

(defn build-text
  "Given a rendered ATIS, build the text atis as a String"
  [atis]
  (let [meta-info (:meta atis)
        wx (:weather atis)
        date (:time wx)]
    (str (:id meta-info)
         " ATIS INFO "
         (first (:info meta-info))
         " "
         (format "%02d" (t/hour date))
         (format "%02d" (t/minute date))
         "Z. "
         (:metar atis)
         " ("
         (join
           " "
           (apply render-numbers 
                  (read-number (:altimeter wx))))
         "). "
         (join " "
           (map 
             build-part
             (:parts atis)))
         " ADVS YOU HAVE INFO "
         (first (:info meta-info))
         ".")))
