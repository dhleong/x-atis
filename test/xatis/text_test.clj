(ns xatis.text-test
  (:require [clojure.test :refer :all]
            [xatis
             [render :as r]
             [render-test :as rt]
             [text :refer :all]]))

(def rendered-atis
  (r/render-atis
    rt/config rt/profile rt/metar "a"))

(deftest build-text-test
  (testing "Basic build"
    (is (= (str "KLGA ATIS INFO A 1251Z. "
                rt/metar-data
                " (THREE ZERO ZERO THREE). ILS APPROACHES IN USE. "
                "ARRS EXPT ILS RWY 22 APPR. DEPS EXPT RWY 31. "
                "NOTAMS BOGUS NOTICE. ADVS YOU HAVE INFO A.")
           (build-text rendered-atis)))))
