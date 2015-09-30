(defproject xatis "0.1.0-SNAPSHOT"
  :description "Cross-platform voice ATIS for Vatsim"
  :url "http://github.com/dhleong/xatis"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories {"fast-md5" "https://dl.bintray.com/fundacionjala/enforce"
                 "mary-tts" "https://dl.bintray.com/marytts/marytts/"
                 "mary-snapshots" "https://oss.jfrog.org/artifactory/oss-snapshot-local"
                 "jtok" "https://dl.bintray.com/dfki-lt/maven/"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [asfiled "0.1.0-SNAPSHOT"]
                 [clj-time "0.11.0"]
                 [de.dfki.mary/marytts-runtime "5.2-SNAPSHOT"]
                 [de.dfki.mary/marytts-lang-en "5.2-SNAPSHOT"]
                 [de.dfki.mary/voice-cmu-slt-hsmm "5.2-SNAPSHOT"]]
  :main ^:skip-aot xatis.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
