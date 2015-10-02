(ns xatis.core
  (:require [clojure.java.io :refer [file]]
            [seesaw.core :refer [native!] :as s]
            [xatis
             [config :refer [show-config]]
             [vatis :refer [read-file]]])
  (:gen-class))

(defn read-profile
  [profile]
  (if-let [config (read-file profile)]
    config
    (s/alert (str "Unable to read profile "
                  (.getAbsolutePath profile)
                  ".")
             :type :warning)))

(defn open-config
  [profile]
  {:pre [(instance? java.io.File profile)]}
  (if (.exists profile)
    (show-config (read-profile profile))
    (s/alert (str "Specified profile "
                  (.getAbsolutePath profile)
                  " does not exist.")
             :type :warning)))

(defn pick-profile-file
  [callback]
  (println "Provide the file for now"))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (native!)
  (if (empty? args)
    (pick-profile-file open-config)
    (open-config (file (first args)))))
