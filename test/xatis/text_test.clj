(ns xatis.text-test
  (:require [clojure.test :refer :all]
            [xatis
             [render :as r]
             [render-test :as rt]
             [text :refer :all]]))

(def rendered-atis
  (r/render-atis
    rt/config rt/profile rt/metar "a"))

(deftest expand-taxiways-test
  (testing "Letters only"
    (is (= "TWY E" (expand-taxiways "TWY %E")))
    (is (= "TWY ZA" (expand-taxiways "TWY %ZA"))))
  (testing "Letters and Numbers"
    (is (= "TWY E6" (expand-taxiways "TWY %E6")))
    (is (= "TWY E16" (expand-taxiways "TWY %E16")))
    (is (= "TWY EE21" (expand-taxiways "TWY %EE21")))))

(deftest expand-navaids-test
  (testing "Clean airports and navaids"
    (is (= "KJFK" (expand-airports-navaids
                               "@KJFK")))
    (is (= "GBN" (expand-airports-navaids
                               "*GBN")))))

(deftest expand-numbers-test
  (testing "Clean airports and navaids"
    (is (= "1234" (expand-numbers "#1234")))))


(deftest build-text-test
  (testing "Basic build"
    (is (= (str "KLGA ATIS INFO A 1251Z. "
                rt/metar-data
                " (THREE ZERO ZERO THREE). ILS APPROACHES IN USE. "
                "ARRS EXPT ILS RWY 22 APPR. DEPS EXPT RWY 31. "
                "CONVERGING RWY OPS IN EFFECT. "
                "NOTICES TO AIRMEN; BOGUS NOTICE. "
                "READBACK ALL RWY HS INSTRUCTIONS. "
                "ADVS YOU HAVE INFO A.")
           (build-text rendered-atis)))))

