(ns ^{:author "Daniel Leong"
      :doc "Take a rendered ATIS object and build the text
            used to create a voice ATIS"}
  xatis.voice
  (:require [clojure.string :refer [join split trim upper-case] :as s]
            [clj-time.core :as t]
            [xatis
             [abbr :refer [expand-abbrs]]
             [render :refer [read-number]]
             [util :refer [typed-dispatch-fn]]]))

(def numbers-to-text
  {\0 "ZERO" \1 "ONE" \2 "TWO" \3 "THREE"
   \4 "FOUR" \5 "FIVE" \6 "SIX" \7 "SEVEN"
   \8 "EIGHT" \9 "NINER"})

(def non-ceiling-types
  #{:few :scattered})
(def ceiling-types
  #{:broken :overcast})

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

(defn read-long-number
  [number]
  (let [as-str (str number)
        ten-thousands? (>= number 10000)
        rest-num (if ten-thousands?
                   (Integer/parseInt (subs as-str 1))
                   number)
        rest-small? (< rest-num 1000)]
    (trim
      (str 
        (when ten-thousands?
          (str (-> as-str
                   (subs 0 1)
                   render-numbers
                   first)))
        (when rest-small?
          " ZERO THOUSAND")
        (when (> rest-num 0)
          (str " " rest-num))))))

(defn render-winds
  [metar]
  (let [wind (:wind metar)
        vrb? (= :vrb (:dir wind))
        vrb-dir (:dir-variable wind)
        base 
        [(cond
           vrb?  "VARIABLE"
           vrb-dir (->> vrb-dir
                        (map #(string-read-number % 3))
                        (join " VARIABLE "))
           :else (render-read-number (:dir wind) 3))
         "AT"
         (render-read-number (:speed wind))]]
    (if-let [gust (:gust wind)]
      (concat base ["GUST" (render-read-number gust)])
      base)))

(defn expand-runways
  "Expands strings that are obviously runways
  (such as 22R or 04C)"
  [text]
  (let [m (re-matcher #"\b[0-9]{1,2}[CLR]\b" text)]
    (if-let [runway (re-find m)]
      (let [part-start (dec (count runway))
            part (subs runway part-start)
            number (Integer/parseInt
                     (subs runway 0 part-start))]
        (recur (.replaceFirst
                 m 
                 (trim
                   (str
                     (string-read-number number)
                     (case part
                       "C" " CENTER"
                       "L" " LEFT"
                       "R" " RIGHT"))))))
      ;; nothing more to do here
      text)))

(defn expand-numbers
  [text]
  (s/replace text #"([0-9])" " $1"))

(defn build-clouds
  [wx]
  (->> (:sky wx)
       (filter #(not (contains? ceiling-types (:type %))))
       (map #(let [feet (read-long-number (:ceiling %))]
               (str (upper-case (name (:type %)))
                    " CLOUDS AT "
                    feet
                    ".")))
       (join " ")))

(defn build-ceilings
  [wx & [my-ceiling-types]]
  (->> (:sky wx)
       (filter #(contains? ceiling-types (:type %)))
       (map #(let [feet (read-long-number (:ceiling %))]
               (str feet
                    " "
                    (upper-case (name (:type %)))
                    ".")))
       (join " ")))

(defn build-precipitation
  [metar]
  (->> (:weather metar)
       (map #(str (trim %) ". "))
       join
       upper-case))

(defmulti build-part typed-dispatch-fn)
(defmethod build-part :vector
  [part]
  (expand-abbrs (join part)))
(defmethod build-part :default
  [part]
  (-> part
      expand-runways
      expand-abbrs
      expand-numbers))

(defmulti build-rvr typed-dispatch-fn)
(defmethod build-rvr :vector
  [rvrs]
  (->> rvrs
       (map build-rvr)
       (join " ")))
(defmethod build-rvr :map
  [rvr]
  (let [vis (:visibility rvr)
        final (or (:is vis) (:to vis))]
    (str "RUNWAY "
         (expand-runways (:runway rvr))
         " RVR "
         (when-let [from (:from vis)]
           (str from " VARIABLE TO "))
         (case (:as vis)
           :less-than "LESS THAN "
           :more-than "GREATER THAN "
           "") ;; default
         final
         ". ")))
(defmethod build-rvr :default [_] "") ;; no rvr


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
         "WIND " (join " " (flatten (render-winds wx))) ". "
         "VISIBILITY " (string-read-number (:visibility wx)) ". "
         (render-precipitation wx)
         (let [clouds (build-clouds wx)]
           (when-not (empty? clouds)
             (str clouds " ")))
         (let [ceil (build-ceilings wx)]
           (when-not (empty? ceil)
             (str "CEILING " ceil " ")))
         (build-rvr (:rvr wx))
         "TEMPERATURE " (string-read-number (:value (:temperature wx)))
         ", DEWPOINT " (string-read-number (:dewpoint (:temperature wx)))
         ". ALTIMETER " (string-read-number (:altimeter wx) 4)
         ". "
         ;; all the parts
         (let [parts  
               (join " "
                     (map 
                       build-part
                       (:parts atis)))]
           (when (not (empty? parts))
             (str parts " ")))
         "Advise on initial contact you have information "
         (:info meta-info)
         ".")))
