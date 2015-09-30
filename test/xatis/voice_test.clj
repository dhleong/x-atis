(ns xatis.voice-test
  (:require [clojure.test :refer :all]
            [xatis
             [render :as r]
             [render-test :as rt]
             [voice :refer :all]]))

(def rendered-atis
  (r/render-atis
    rt/config rt/profile rt/metar "a"))

(defn build-weather
  [weather]
  (build-voice 
    (r/render-atis
      {:facility "LaGuardia"} 
      {} 
      weather
      "a")))

;;
;; Tests
;;

(deftest expand-runways-test
  (testing "Expand 22R"
    (is (= "TWO TWO RIGHT" (expand-runways "22R"))))
  (testing "Expand Multiple"
    (is (= "TWO TWO RIGHT, and FOUR LEFT" (expand-runways "22R, and 04L"))))
  (testing "DON'T expand random numbers"
    (is (= "2200" (expand-runways "2200")))))

(deftest build-ceilings-test
  (testing "Below 10,000"
    (is (= "2540 BROKEN."
           (build-ceilings
             {:sky [{:ceiling 2540 :type :broken}]}))))
  (testing "Above 10,000"
    (is (= "ONE 2540 BROKEN." 
           (build-ceilings
             {:sky [{:ceiling 12540 :type :broken}]}))))
  (testing "At or near 10,000"
    (is (= "ONE ZERO THOUSAND BROKEN." 
           (build-ceilings
             {:sky [{:ceiling 10000 :type :broken}]})))
    (is (= "ONE ZERO THOUSAND 500 BROKEN." 
           (build-ceilings
             {:sky [{:ceiling 10500 :type :broken}]})))))

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
           (build-voice rendered-atis))))
  (testing "No Ceiling"
    (is (= (str
             "LaGuardia Airport Information Alpha. "
             "ONE TWO FIVE ONE ZULU. "
             "WIND ZERO ONE ZERO AT FOUR. "
             "VISIBILITY FOUR. "
             "TEMPERATURE TWO FOUR, DEWPOINT TWO TWO. "
             "ALTIMETER TWO NINER SIX SIX. "
             "Advise on initial contact you have information Alpha.")
           (build-weather
             "KLGA 301251Z 01004KT 4SM 24/22 A2966"))))
  (testing "Fancy Weather"
    (is (= (str
             "LaGuardia Airport Information Alpha. "
             "ONE TWO FIVE ONE ZULU. "
             "WIND TWO TWO ZERO VARIABLE ZERO THREE ZERO AT ONE FOUR. "
             "VISIBILITY FOUR. "
             "LIGHT RAIN. MIST. FEW CLOUDS AT 1500. "
             "SCATTERED CLOUDS AT 2600. "
             "CEILING 4400 BROKEN. TWO 5000 OVERCAST. "
             "RUNWAY FOUR RIGHT RVR 5500 VARIABLE TO GREATER THAN 6000. "
             "TEMPERATURE TWO FOUR, DEWPOINT TWO TWO. "
             "ALTIMETER TWO NINER SIX SIX. "
             "Advise on initial contact you have information Alpha.")
           (build-weather
             "KLGA 301251Z 220V03014KT 4SM R04R/5500VP6000FT -RA BR
             FEW015 SCT026 BKN044 OVC250 24/22 A2966")))))

(def blabla
  (str "light rain mist; few clouds a 1500; ceiling 4400 broken"
       "rwy 4r rvr 5500 variable to greater than 6000"))
