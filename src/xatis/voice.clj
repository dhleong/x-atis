(ns ^{:author "Daniel Leong"
      :doc "Take a rendered ATIS object and build the text
            used to create a voice ATIS"}
  xatis.voice
  (:require [clojure.string :refer [join split upper-case] :as s]
            [clj-time.core :as t]))

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
