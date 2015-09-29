(ns xatis.render-test
  (:require [clojure.test :refer :all]
            [xatis.render :refer :all]
            [asfiled.metar :refer [decode-metar]]))

(def metar
  (decode-metar 
    "KLGA 311251Z 09005G10KT 10SM BKN090 BKN140 BKN250 26/19 A3003"))

(def config
  {:id "KLGA"
   :facility "LaGuardia"})

(def profile
  {:localize-approach false, :converging-ops true,
   :arriving-rwys nil, :magnetic-add true,
   :readback-assigned-alt false, :eu-parse false,
   :magnetic-variation true, :readback-callsign false,
   :simul-approachs false, :name "L ILS 22 D 31",
   :hazardous-weather-area nil, :windshear-advs false,
   :magnetic-degrees "10", :ctc-position nil,
   :visual-approach false, :rnav-approach false,
   :hold-short-intructions true, :land-hold-short false,
   :verify-sid false, :normal-update true, :notam nil,
   :vor-approach false, :departing-rwys nil,
   :vfr-direction false, :simul-approach-intersecting false,
   :dme-approach false, :ils-approach false,
   :normal-update-time "51",
   :dep-arr-notice "ARRS EXPT ILS RWY 22 APPR. DEPS EXPT RWY 31.",
   :grass-ops false, :ctc-freq nil, :mode-charlie false,
   :deps-ctc-freq false, :hazardous-weather false,
   :magnetic-subtract false})

(deftest read-number-test
  (testing "Single digit"
    (is (= [2] (read-number 2))))
  (testing "Multi-digit"
    (is (= [2 4 6] (read-number 246))))
  (testing "Padded"
    (is (= [0 4 6] (read-number 46 3)))))

(deftest render-test
  (testing "Basic render to array"
    (let [result (render-atis config profile metar "a")]
      (is (= "LaGuardia" (nth result 0)))
      (is (= "Airport Information Alpha." (nth result 1)))
      (is (= [1 2 5 1] (nth result 2)))
      (is (= \Z (nth result 3)))
      (is (= [0 9 0] (nth result 5)))
      (is (= [5] (nth result 7)))
      (is (= "Gust" (nth result 8)))
      (is (= [1 0] (nth result 9))))))
