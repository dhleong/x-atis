(ns xatis.voice-test
  (:require [clojure.test :refer :all]
            [xatis
             [render :as r]
             [render-test :as rt]
             [voice :refer :all]]))

(def rendered-atis
  (r/render-atis
    rt/config rt/profile rt/metar "a"))

(deftest build-ceilings-test
  (testing "Below 10,000"
    (is (= "2540 BROKEN."
           (build-ceilings
             {:sky [{:ceiling 2540 :type :broken}]}))))
  (testing "Above 10,000"
    (is (= "ONE 2540 BROKEN." 
           (build-ceilings
             {:sky [{:ceiling 12540 :type :broken}]})))))

(deftest build-voice-test
  (testing "Basic Build"
    (is (= (str
             "LaGuardia Airport Information Alpha. "
             "ONE TWO FIVE ONE ZULU. "
             "WIND ZERO NINER ZERO AT FIVE GUST ONE ZERO. "
             "VISIBILITY ONE ZERO. "
             "CEILING 9000 BROKEN. ONE 4000 BROKEN. TWO 5000 BROKEN. "
             "TEMPERATURE TWO SIX, DEWPOINT ONE NINER. "
             "ALTIMETER THREE ZERO ZERO THREE. "
             "ILS APPROACHES IN USE. "
             "ARRIVALS EXPECT ILS RUNWAY  2 2 APPROACH. "
             "DEPARTURES EXPECT RUNWAY  3 1. "
             "CONVERGING RUNWAY OPERATIONS IN EFFECT. "
             "NOTICES TO AIRMEN: BOGUS NOTICE. "
             "READBACK ALL RUNWAY HOLD SHORT INSTRUCTIONS. "
             "Advise on initial contact you have information Alpha.")
           (build-voice rendered-atis)))))
