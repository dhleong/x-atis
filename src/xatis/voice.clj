(ns ^{:author "Daniel Leong"
      :doc "Take a rendered ATIS object and build the text
            used to create a voice ATIS"}
  xatis.voice
  (:require [clojure.string :refer [join split trim upper-case] :as s]
            [clj-time.core :as t]
            [asfiled.skyvector :refer [get-vor]]
            [xatis
             [abbr :refer [expand-abbrs]]
             [render :refer [nato read-number]]
             [util :refer [re-replace typed-dispatch-fn]]]))

(def numbers-to-text
  {\0 "ZERO" \1 "ONE" \2 "TWO" \3 "THREE"
   \4 "FOUR" \5 "FIVE" \6 "SIX" \7 "SEVEN"
   \8 "EIGHT" \9 "NINER"})

(def teens-to-text
  {10 "TEN"
   11 "ELEVEN"
   12 "TWELVE"
   13 "THIRTEEN"
   14 "FOURTEEN"
   15 "FIFTEEN"
   16 "SIXTEEN"
   17 "SEVENTEEN"
   18 "EIGHTEEN"
   19 "NINETEEN"})

(def tens-to-text
  {2 "TWENTY"
   3 "THIRTY"
   4 "FOURTY"
   5 "FIFTY"
   6 "SIXTY"
   7 "SEVENTY"
   8 "EIGHTY"
   9 "NINETY"})

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
  (let [ten-thousands (int (/ number 10000))
        thousands (int (/ (mod number 10000) 1000))
        hundreds (int (/ (mod number 1000) 100))
        tens (int (/ (mod number 100) 10))
        ones (int (mod number 10))
        ten-thousands? (> ten-thousands 0)]
    (->> [(when ten-thousands?
            (string-read-number ten-thousands))
          (when (or ten-thousands?
                    (> thousands 0))
            (str (string-read-number thousands)
                 " THOUSAND"))
          (when (> hundreds 0)
            (str (string-read-number hundreds)
                 " HUNDRED"))
          (cond
            (= tens 1) (get teens-to-text (+ (* tens 10) ones))
            (> tens 1) (str (get tens-to-text tens)))
          (when (or (= number 0)
                    (and (not= tens 1)
                         (> ones 0))) 
            (string-read-number ones))]
         (filter (complement nil?))
         (join " "))))

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

(defn render-letters
  [letters]
  (join " " (map nato letters)))

(defn expand-taxiways
  "Expands strings that are obviously runways
  (such as 22R or 04C)"
  [text]
  (->> text
       (re-replace 
         #"%([A-Z]{1,2})([0-9]*)\b" 
         #(let [letters (second %)
                nato-letters (render-letters letters)
                number (last %)]
            (if (empty? number)
              nato-letters
              (str
                nato-letters
                " "
                (read-long-number (Integer/parseInt number))))))))

(defn expand-runways
  "Expands strings that are obviously runways
  (such as 22R or 04C)"
  [text]
  (->> text
       (re-replace 
         #"\b[0-9]{1,2}[CLR]\b" 
         #(let [runway %
                part-start (dec (count runway))
                part (subs runway part-start)
                number (Integer/parseInt
                         (subs runway 0 part-start))]
            (trim
              (str
                (string-read-number number)
                (case part
                  "C" " CENTER"
                  "L" " LEFT"
                  "R" " RIGHT")))))))

(defn expand-airports-navaids
  [text]
  (->> text
       (re-replace
         #"(@[A-Z]{3,4}|\*[A-Z]{3})\b"
         #(let [match (first %)
                kind (if (= \@ (first match))
                       :airport
                       :navaid)
                icao (subs match 1)
                data (get-vor "KLGA" icao)] ;; `from` is irrelevant for us
            (if-let [full-name (:name data)]
              (str full-name
                   (when (and (= :airport kind)
                          (= -1 (.indexOf (upper-case full-name) "AIRPORT")))
                     " AIRPORT"))
              (case kind
                :airport (str (render-letters icao) " AIRPORT")
                :navaid (render-letters icao)))))))

(defn expand-frequencies
  "Expand frequencies. MUST be all six digits"
  [text]
  (->> text
       (re-replace
         #"\b[0-9]{3}\.[0-9]{3}\b"
         #(str
            (string-read-number
              (Integer/parseInt
                (subs % 0 3)))
            " POINT "
            (string-read-number
              (Integer/parseInt
                (subs % 4)))))))

(defn expand-numbers
  [text]
  (->> text
       (re-replace
         #"#[0-9]+\b" 
         #(read-long-number (Integer/parseInt (subs % 1))))
       (re-replace
         #"\b[0-9]+\b"
         #(string-read-number (Integer/parseInt %)))))

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
      expand-taxiways
      expand-airports-navaids
      expand-abbrs
      expand-frequencies
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
           (str (read-long-number from) " VARIABLE TO "))
         (case (:as vis)
           :less-than "LESS THAN "
           :more-than "GREATER THAN "
           "") ;; default
         (read-long-number final)
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
         (build-precipitation wx)
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
