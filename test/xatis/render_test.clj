(ns xatis.render-test
  (:require [clojure.test :refer :all]
            [xatis.render :refer :all]))

(def metar-data
  "09005G10KT 10SM BKN090 BKN140 BKN250 26/19 A3003")
(def metar
  (str "KLGA 281251Z " metar-data))

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
   :verify-sid false, :normal-update true, :notam "BOGUS NOTICE.",
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
    (is (= "LDG RWY 1." (render-runways "LDG" "01")))
    (is (= "LDG RWY 1L." (render-runways "LDG" "01L"))))
  (testing "Two runways"
    (is (= "LDG RWYS 1, AND 2."
           (render-runways "LDG" "01,02")))
    (is (= "LDG RWYS 1L, AND 2."
           (render-runways "LDG" "01L,02"))))
  (testing "Three runways"
    (is (= "LDG RWYS 1, 2, AND 3."
           (render-runways "LDG" "01,02,03"))))
  (testing "Weird separators"
    (is (= "LDG RWYS 1, 2, 3, AND 4."
           (render-runways "LDG" "01:02;|03 04")))))

(deftest render-test
  (testing "Render nil when no letter is provided"
    (is (nil? (render-atis config profile metar "")))
    (is (nil? (render-atis config profile metar nil))))
  (testing "Basic render to array"
    (let [parts (:parts (render-atis config profile metar "a"))]
      (is (= "ILS APPROACHES IN USE." (nth parts 0)))
      (is (= "ARRS EXPT ILS RWY 22 APPR. DEPS EXPT RWY 31."
             (nth parts 1)))
      (is (= "CONVERGING RWY OPS IN EFFECT."
             (nth parts 2)))
      (is (= "NOTAMS"
             (nth parts 3)))
      (is (= "BOGUS NOTICE."
             (nth parts 4)))))
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
      (is (= "LDG RWY 4." (nth parts 0)))
      (is (= "DEPTG RWY 11." (nth parts 1)))))
  (testing "Hazardous Weather"
    (let [parts (:parts
                   (render-atis config 
                                {:hazardous-weather true
                                 :hazardous-weather-area "New York"}
                                metar "a"))]
      (is (= (str "ATTN ALL ACFT, HAZ WX INFO FOR "
                  "New York AREA AVBL FROM ATC BY REQUEST.")
             (nth parts 0)))))
  (testing "Contact prior"
    (let [parts (:parts
                   (render-atis config 
                                {:deps-ctc-freq true
                                 :ctc-position "LOS ANGELES GROUND"
                                 :ctc-freq "121.650"}
                                metar "a"))]
      (is (= (str "ALL DEPS CTC LOS ANGELES GROUND "
                  "ON 121.650 PRIOR TO TAXI.")
             (nth parts 0)))))
  (testing "Drop empty strings"
    (let [parts (:parts
                   (render-atis config 
                                {:notam ""}
                                metar "a"))]
      (is (empty?  parts)))))
