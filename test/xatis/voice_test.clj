(ns xatis.voice-test
  (:require [clojure.test :refer :all]
            [asfiled.skyvector :refer [get-vor]]
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

(defmacro expand-navaids-with
  [get-vor-result text]
  `(with-redefs-fn {#'get-vor (constantly ~get-vor-result)}
     #(expand-airports-navaids ~text)))

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

(deftest expand-taxiways-test
  (testing "Letters only"
    (is (= "TWY Echo" (expand-taxiways "TWY %E")))
    (is (= "TWY Zulu Alpha" (expand-taxiways "TWY %ZA"))))
  (testing "Letters and Numbers"
    (is (= "TWY Echo SIX" (expand-taxiways "TWY %E6")))
    (is (= "TWY Echo SIXTEEN" (expand-taxiways "TWY %E16")))
    (is (= "TWY Echo Echo TWENTY ONE" (expand-taxiways "TWY %EE21")))))

(deftest expand-navaids-test
  (testing "Known Airport"
    ;; terrible hacks to avoid brittleness 
    ;;  inherent with network calls
    (is (= "Kennedy AIRPORT" (expand-navaids-with
                               {:name "Kennedy"}
                               "@KJFK")))
    (is (= "GILA BEND" (expand-navaids-with
                               {:name "GILA BEND"}
                               "*GBN"))))
  (testing "Fallback gracefully"
    ;; terrible hacks to avoid brittleness 
    ;;  inherent with network calls
    (is (= "Kilo Juliett Foxtrot Kilo AIRPORT" (expand-navaids-with
                                                 {:name nil}
                                                 "@KJFK")))
    (is (= "Golf Bravo November" (expand-navaids-with
                                   {:name nil}
                                   "*GBN")))))

(deftest expand-frequencies-test
  (testing "Expand Full frequency"
    (is (= "ONE TWO ONE POINT NINER SEVEN FIVE"
           (expand-frequencies "121.975")))))

(deftest expand-numbers-test
  (testing "Non-prefixed numbers"
    (is (= "TWO FOUR SIX" 
           (expand-numbers "246"))))
  (testing "Prefixed numbers are expanded via read-long"
    (is (= "TWO HUNDRED FOURTY SIX" 
           (expand-numbers "#246"))))
  (testing "All together now"
    (is (= "TWO FOUR SIX TWO HUNDRED FOURTY SIX" 
           (expand-numbers "246 #246")))
    (is (= "TWO HUNDRED FOURTY SIX TWO FOUR SIX" 
           (expand-numbers "#246 246")))))

(deftest read-long-number-test
  (testing "Small numbers"
    (is (= "ZERO" (read-long-number 0)))
    (is (= "ONE" (read-long-number 1)))
    (is (= "TEN" (read-long-number 10))))
  (testing "Teens"
    (is (= "ELEVEN" (read-long-number 11)))
    (is (= "TWELVE" (read-long-number 12))))
  (testing "Tens"
    (is (= "TWENTY" (read-long-number 20)))
    (is (= "TWENTY ONE" (read-long-number 21))))
  (testing "Hundreds"
    (is (= "ONE HUNDRED" (read-long-number 100)))
    (is (= "ONE HUNDRED ONE" (read-long-number 101)))
    (is (= "ONE HUNDRED ELEVEN" (read-long-number 111)))
    (is (= "ONE HUNDRED TWENTY" (read-long-number 120)))
    (is (= "ONE HUNDRED TWENTY ONE" (read-long-number 121))))
  (testing "Thousands"
    (is (= "ONE THOUSAND" (read-long-number 1000)))
    (is (= "ONE THOUSAND ONE" (read-long-number 1001)))
    (is (= "ONE THOUSAND ELEVEN" (read-long-number 1011)))
    (is (= "ONE THOUSAND TWENTY ONE" (read-long-number 1021)))
    (is (= "ONE THOUSAND ONE HUNDRED ONE" (read-long-number 1101)))
    (is (= "ONE THOUSAND ONE HUNDRED ELEVEN" (read-long-number 1111)))
    (is (= "ONE THOUSAND ONE HUNDRED TWENTY" (read-long-number 1120)))
    (is (= "ONE THOUSAND ONE HUNDRED TWENTY ONE" (read-long-number 1121)))))

(deftest render-winds-test
  (testing "Cross 360 adding"
    (= ["ZERO ONE ZERO" "AT" "FIVE"] 
       (render-winds
         {:magnetic-add 20
          :magnetic-variation true}
         {:wind {:dir 350
                 :speed 5}})))
  (testing "Cross 360 subtracting"
    (= ["THREE FIVE ZERO" "AT" "FIVE"] 
       (render-winds
         {:magnetic-add -20
          :magnetic-variation true}
         {:wind {:dir 10
                 :speed 5}}))))

(deftest build-ceilings-test
  (testing "Below 10,000"
    (is (= "TWO THOUSAND FIVE HUNDRED FOURTY BROKEN."
           (build-ceilings
             {:sky [{:ceiling 2540 :type :broken}]}))))
  (testing "Above 10,000"
    (is (= "ONE TWO THOUSAND FIVE HUNDRED FOURTY BROKEN." 
           (build-ceilings
             {:sky [{:ceiling 12540 :type :broken}]}))))
  (testing "At or near 10,000"
    (is (= "ONE ZERO THOUSAND BROKEN." 
           (build-ceilings
             {:sky [{:ceiling 10000 :type :broken}]})))
    (is (= "ONE ZERO THOUSAND FIVE HUNDRED BROKEN." 
           (build-ceilings
             {:sky [{:ceiling 10500 :type :broken}]})))))

(deftest build-voice-test
  (testing "Basic Build"
    (is (= (str
             "LaGuardia Airport Information Alpha. "
             "ONE TWO FIVE ONE ZULU. "
             "WIND ONE ZERO ZERO AT FIVE GUST ONE ZERO. " ;; note the magnetic variation
             "VISIBILITY ONE ZERO. "
             "CEILING NINER THOUSAND BROKEN. ONE FOUR THOUSAND BROKEN. "
             "TWO FIVE THOUSAND BROKEN. "
             "TEMPERATURE TWO SIX, DEWPOINT ONE NINER. "
             "ALTIMETER THREE ZERO ZERO THREE. "
             "ILS APPROACHES IN USE. "
             "ARRIVALS EXPECT ILS RUNWAY TWO TWO APPROACH. "
             "DEPARTURES EXPECT RUNWAY THREE ONE. "
             "CONVERGING RUNWAY OPERATIONS IN EFFECT. "
             "NOTICES TO AIRMEN: BOGUS NOTICE. "
             "READBACK ALL RUNWAY HOLD SHORT INSTRUCTIONS. "
             "Advise on initial contact you have information Alpha.")
           (build-voice rendered-atis)))))

(deftest voice-weather-tests
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
            "KLGA 301251Z 01004KT 4SM 24/22 A2966")))))
  (testing "Fancy Weather"
    (is (= (str
             "LaGuardia Airport Information Alpha. "
             "ONE TWO FIVE ONE ZULU. "
             "WIND TWO TWO ZERO VARIABLE ZERO THREE ZERO AT ONE FOUR. "
             "VISIBILITY FOUR. "
             "LIGHT RAIN. MIST. FEW CLOUDS AT ONE THOUSAND FIVE HUNDRED. "
             "SCATTERED CLOUDS AT TWO THOUSAND SIX HUNDRED. "
             "CEILING FOUR THOUSAND FOUR HUNDRED BROKEN. "
             "TWO FIVE THOUSAND OVERCAST. "
             "RUNWAY FOUR RIGHT RVR FIVE THOUSAND FIVE HUNDRED "
             "VARIABLE TO GREATER THAN SIX THOUSAND. "
             "TEMPERATURE TWO FOUR, DEWPOINT TWO TWO. "
             "ALTIMETER TWO NINER SIX SIX. "
             "Advise on initial contact you have information Alpha.")
           (build-weather
             "KLGA 301251Z 220V03014KT 4SM R04R/5500VP6000FT -RA BR
             FEW015 SCT026 BKN044 OVC250 24/22 A2966"))))
