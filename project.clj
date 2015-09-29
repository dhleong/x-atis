(defproject xatis "0.1.0-SNAPSHOT"
  :description "Cross-platform voice ATIS for Vatsim"
  :url "http://github.com/dhleong/xatis"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [asfiled "0.1.0-SNAPSHOT"]
                 [clj-time "0.11.0"]]
  :main ^:skip-aot xatis.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
