(ns xatis.abbr-test
  (:require [clojure.test :refer :all]
            [xatis.abbr :refer :all]))

(deftest abbreviations-test
  (testing "Abbreviations expansion"
    (is (= "NOTICES TO AIRMEN:"
          (expand-abbrs "NOTAMS")))))
