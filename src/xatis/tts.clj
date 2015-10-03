(ns ^{:author "Daniel Leong"
      :doc "Speak a built voice ATIS String"}
  xatis.tts
  (:require [clojure.string :as s])
  (:import [java.util Locale]
           [javax.sound.sampled LineEvent LineEvent$Type LineListener]
           [marytts LocalMaryInterface]
           [marytts.util.data.audio AudioPlayer]))

;;
;; Constants
;;

(def replacements
  (->>
    ["ILS" "I L S"
     "RVR" "R V R"
     "VOR" "V O R"
     "DME" "D M E"
     "READBACK" "REED BACK"] ;; otherwise it sounds like RED BACK
    (partition 2)
    (map 
      (fn [[k v]]
        (if (string? k)
          [(re-pattern (str "\\b" k "\\b")) v]
          [k v])))))

;;
;; Shared marytts obj
;;

(defn- init-mary
  []
  (let [mary (LocalMaryInterface.)]
    (doto mary
      (.setLocale Locale/US)
      (.setVoice (first (.getAvailableVoices mary))))))

(def marytts (init-mary))

;;
;; Internal utils
;;

(defn- perform-speech
  [audio & [on-finished]]
  (doto (if on-finished
          (AudioPlayer. 
            audio
            (reify LineListener 
              (update [this event]
                (when (= LineEvent$Type/STOP (.getType event))
                  (on-finished)))))
          (AudioPlayer. audio))
    (.start)))

(defn process-text
  "Not all TTS systems are the same. Mary has some
  quirks of her own. Let's help her out."
  [input]
  (loop [output input
         repls replacements]
    (if-let [[k v] (first repls)]
      (recur (s/replace output k v) (rest repls))
      output)))

;;
;; Public interface
;;

(defn generate-speech
  [text]
  (->> text
       process-text
       (.generateAudio marytts)))

(defn preview-speech
  [text-or-audio & [on-finished]]
  (if (string? text-or-audio)
    (perform-speech (generate-speech text-or-audio) on-finished)
    (perform-speech text-or-audio on-finished)))
