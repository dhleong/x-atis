(ns ^{:author "Daniel Leong"
      :doc "Take a rendered ATIS object and build the text
            used to create a voice ATIS"}
  xatis.voice
  (:require [clojure.string :refer [join split upper-case] :as s]
            [clj-time.core :as t]
            [xatis.abbr :refer [expand-abbrs]]))

(def numbers-to-text
  {\0 "ZERO" \1 "ONE" \2 "TWO" \3 "THREE"
   \4 "FOUR" \5 "FIVE" \6 "SIZE" \7 "SEVEN"
   \8 "EIGHT" \9 "NINER"})

(defn render-numbers
  "Given a vector of numbers, translate them into text that
  a TTS system can easily render into speech"
  [& numbers]
  (map 
    #(get numbers-to-text (.charAt (str %) 0))
    numbers))

(defn expand-numbers
  [text]
  (s/replace text #"([0-9])" " $1"))

(defmulti build-part (fn [a]
                       (cond
                         (vector? a) :vector
                         (string? a) :string
                         :else :default)))
(defmethod build-part :vector
  [part]
  (expand-abbrs (join part)))
(defmethod build-part :default
  [part]
  (-> part
      expand-abbrs
      expand-numbers))

(defn build-voice
  "Given a rendered ATIS, build the text that should
  be read for the Voice ATIS"
  [atis]
  (let [meta-info (:meta atis)
        wx (:weather atis)
        date (:time wx)]
    (str (:facility meta-info)
         " Airport Information "
         (:info meta-info)
         ". "
         (join " "
           (map 
             build-part
             (:parts atis)))
         " Advise on initial contact you have information "
         (:info meta-info)
         ".")))
