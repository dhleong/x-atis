(ns xatis.render-test
  (:require [clojure.test :refer :all]
            [xatis.render :refer :all]))

(def metar
  "KLGA 311251Z 09005G10KT 10SM BKN090 BKN140 BKN250 26/19 A3003")

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
   :verify-sid false, :normal-update true, :notam "BOGUS NOTAM",
   :vor-approach false, :departing-rwys nil,
   :vfr-direction false, :simul-approach-intersecting false,
   :dme-approach false, :ils-approach true,
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

(deftest render-runways-test
  (testing "Single runway"
    (is (= ["LDG RWY " [1]] (render-runways "LDG" "01")))
    (is (= ["LDG RWY " [1] "L"] (render-runways "LDG" "01L"))))
  (testing "Two runways"
    (is (= ["LDG RWYS " [1] ", AND " [2]]
           (render-runways "LDG" "01,02")))
    (is (= ["LDG RWYS " [1] "L" ", AND " [2]]
           (render-runways "LDG" "01L,02"))))
  (testing "Three runways"
    (is (= ["LDG RWYS " [1] ", " [2] ", AND " [3]]
           (render-runways "LDG" "01,02,03")))))

(deftest render-test
  (testing "Basic render to array"
    (let [parts (:parts (render-atis config profile metar "a"))]
      (is (= "ILS APPROACHES IN USE." (nth parts 0)))
      (is (= "ARRS EXPT ILS RWY 22 APPR. DEPS EXPT RWY 31."
             (nth parts 1)))
      (is (= "NOTAMS"
             (nth parts 2)))
      (is (= "BOGUS NOTAM"
             (nth parts 3)))))
  (testing "ILS *and* Visual"
    (let [result (render-atis config 
                             {:visual-approach true
                              :ils-approach true}
                             metar "a")
          parts (:parts result)]
      (is (= "VISUAL, AND ILS APPROACHES IN USE." (nth parts 0)))))
  (testing "Visual only"
    (let [result (render-atis config {:visual-approach true} metar "a")
          parts (:parts result)]
      (is (= "VISUAL APPROACHES IN USE." (nth parts 0)))))
  (testing "Runway configs"
    (let [parts (:parts
                  (render-atis config {:arriving-rwys "04"
                                       :departing-rwys "11"}
                               metar "a"))]
      (is (= "LDG RWY " (nth parts 0)))
      (is (= [4] (nth parts 1)))
      (is (= "DEPTG RWY " (nth parts 2)))
      (is (= [1 1] (nth parts 3))))))
