(ns ^{:author "Daniel Leong"
      :doc "Take a rendered ATIS object and build the text
            used to create a voice ATIS"}
  xatis.voice
  (:require [clojure.string :refer [join split upper-case] :as s]
            [clj-time.core :as t]
            [xatis
             [abbr :refer [expand-abbrs]]
             [render :refer [read-number]]]))

(def numbers-to-text
  {\0 "ZERO" \1 "ONE" \2 "TWO" \3 "THREE"
   \4 "FOUR" \5 "FIVE" \6 "SIX" \7 "SEVEN"
   \8 "EIGHT" \9 "NINER"})

(defn render-numbers
  "Given a vector of numbers, translate them into text that
  a TTS system can easily render into speech"
  [& numbers]
  (map 
    #(get numbers-to-text (.charAt (str %) 0))
    numbers))

(defn render-read-number
  "Convenience"
  [number & [min-length]]
  (apply render-numbers (read-number number min-length)))

(defn string-read-number
  "Convenience"
  [number & [min-length]]
  (join " " (render-read-number number min-length)))

(defn render-winds
  [metar]
  (let [wind (:wind metar)
        vrb? (= :vrb (:dir wind))
        base 
        [(if vrb?
           "VARIABLE"
           (render-read-number (:dir wind) 3))
         "AT"
         (render-read-number (:speed wind))]]
    (if-let [gust (:gust wind)]
      (concat base ["GUST" (render-read-number gust)])
      base)))


(defn expand-numbers
  [text]
  (s/replace text #"([0-9])" " $1"))

(defn build-ceilings
  [wx]
  (->> (:sky wx)
       (filter :ceiling)
       (map #(let [feet (:ceiling %)
                   feet-str (str feet)
                   ten-thousands? (>= feet 10000)
                   rest-feet (if ten-thousands?
                               (Integer/parseInt (subs feet-str 1))
                               feet)]
               (str (when ten-thousands?
                      (str (-> feet-str
                               (subs 0 1)
                               render-numbers
                               first)
                           " "))
                    rest-feet
                    " "
                    (upper-case (name (:type %)))
                    ".")))
       (join " ")))

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
         ;; date
         (string-read-number (t/hour date) 2)
         " "
         (string-read-number (t/minute date) 2)
         " ZULU. "
         ;; weather
         "WIND "
         (join " " (flatten (render-winds wx)))
         ". VISIBILITY " (string-read-number (:visibility wx))
         ". CEILING " (build-ceilings wx)
         " TEMPERATURE " (string-read-number (:value (:temperature wx)))
         ", DEWPOINT " (string-read-number (:dewpoint (:temperature wx)))
         ". ALTIMETER " (string-read-number (:altimeter wx) 4)
         ". "
         ;; all the parts
         (join " "
           (map 
             build-part
             (:parts atis)))
         " Advise on initial contact you have information "
         (:info meta-info)
         ".")))
