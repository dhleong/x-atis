(ns ^{:author "Daniel Leong"
      :doc "Render an ATIS profile to text"}
  xatis.render
  (:require [clojure.string :refer [upper-case]]
            [clj-time.core :as t]))

(def nato
  {\A "Alpha"   \B "Bravo"    \C "Charlie" \D "Delta"
   \E "Echo"    \F "Foxtrot"  \G "Golf"    \H "Hotel"
   \I "India"   \J "Juliett"  \K "Kilo"    \L "Limo"
   \M "Mike"    \N "November" \O "Oscar"   \P "Papa"
   \Q "Quebec"  \R "Romeo"    \S "Sierra"  \T "Tango"
   \U "Uniform" \V "Victor"   \W "Whiskey" \X "X-Ray"
   \Y "Yankee"  \Z "Zulu"})

(defn read-number
  "Convert a number into a vector of digits"
  [number & [min-length]]
  ;; it would be more efficient to strip them
  ;;  off with maths, but... laziness
  (let [fmt (if min-length
              (str "%0" min-length "d")
              "%s")]
    (->> (int number) ;; make sure it's a number
         (format fmt)
         (map #(- (int %) (int \0))))))

(defn render-winds
  [metar]
  (let [wind (:wind metar)
        vrb? (= :vrb (:dir wind))
        base 
        [(if vrb?
           "VRB"
           (read-number (:dir wind) 3))
         "At"
         (read-number (:speed wind))]]
    (if-let [gust (:gust wind)]
      (concat base ["Gust" (read-number gust)])
      base)))

(defn render-atis
  "Renders the atis to a vector of parts, from which
  the text-atis and voice-atis can be more easily generated.
  - Strings should generally be read literally, after 
  replacing abbreviations.
  - Chars should be read as their nato-phonetic letter
  - Numbers should be read out as the normal, english reading.
  - Vectors of numbers should be read out individually, and
  rendered to text separated by whitespace
  `metar` should be the decoded METAR"
  [config profile metar information-letter]
  (let [letter (first (upper-case information-letter))
        nato-letter (get nato letter letter)
        zulu (:time metar)]
    (concat
      [(:facility config)
       (str "Airport Information " nato-letter ".")
       (concat (read-number (t/hour zulu)) 
               (read-number (t/minute zulu)))
       \Z
       "Winds"]
      (render-winds metar)
      ".")))
