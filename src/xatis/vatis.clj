(ns ^{:author "Daniel Leong"
      :doc "Interop with vATIS configs"}
  xatis.vatis
  (:require [clojure
             [xml :as xml]
             [zip :as zip]]
            [clojure.java.io :as io]
            [clojure.data.zip.xml :as zx :refer [xml-> xml1->]])
  (:import [java.io ByteArrayInputStream]
           [java.util.zip GZIPInputStream ZipException]))

(defn- profile-to-map
  [profile]
  (let [node (zip/node profile)]
    (->> node
         :content
         (mapcat 
           (fn [v] 
             [(:tag v) (first (:content v))]))
         (apply hash-map))))

(defn read-stream
  [stream]
  (let [root (-> stream
                 xml/parse
                 zip/xml-zip)
        facility-id (xml1-> root :ID zx/text)
        facility-name (xml1-> root :Name zx/text)
        voice-server (xml1-> root :VoiceServer zx/text)
        frequency (xml1-> root :AtisFrequency zx/text)
        profiles (xml->
                   root
                   :Profiles
                   :Profile
                   profile-to-map)]
    {:id facility-id
     :facility facility-name
     :server voice-server
     :frequency frequency
     :profiles profiles}))

(defn read-file
  [gz-file]
  (try
    (read-stream
      (GZIPInputStream.
        (io/input-stream gz-file)))
    (catch ZipException e
      (read-stream
        (io/input-stream gz-file)))))

(defn read-string
  [string]
  (read-stream
    (ByteArrayInputStream. 
      (.getBytes string))))
