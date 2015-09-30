(ns xatis.voice-test
  (:require [clojure.test :refer :all]
            [xatis
             [render :as r]
             [render-test :as rt]
             [voice :refer :all]]))

(def rendered-atis
  (r/render-atis
    rt/config rt/profile rt/metar "a"))

(deftest build-voice-test
  (testing "Basic Build"
    (is (= (str
             "LaGuardia Airport Information Alpha. "
             "ILS APPROACHES IN USE. "
             "ARRIVALS EXPECT ILS RUNWAY  2 2 APPROACH. "
             "DEPARTURES EXPECT RUNWAY  3 1. "
             "CONVERGING RUNWAY OPERATIONS IN EFFECT. "
             "NOTICES TO AIRMEN: BOGUS NOTICE. "
             "READBACK ALL RUNWAY HOLD SHORT INSTRUCTIONS. "
             "Advise on initial contact you have information Alpha.")
           (build-voice rendered-atis)))))
