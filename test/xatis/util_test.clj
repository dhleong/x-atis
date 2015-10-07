(ns xatis.util-test
  (:require [clojure.test :refer :all]
            [xatis.util :refer :all]))

(deftest find-line-end-test
  (testing "First"
    (is (= 4 (find-line-end 
               "KLGA INFO A"
               0 4)))
    (is (= 4 (find-line-end 
               "KLGA INFO A"
               0 6))))
  (testing "Second"
    (is (= 9 (find-line-end 
               "KLGA INFO A"
               5 4)))
    (is (= 11 (find-line-end 
                "KLGA INFO A"
                5 6)))))

(deftest split-atis-test
  (testing "Single line"
    (is (= ["KLGA INFO A"]
           (split-atis "KLGA INFO A"))))
  (testing "Two lines"
    (is (= ["KLGA" "INFO A"]
           (split-atis "KLGA INFO A" 6))))
  (testing "Multi-line"
    (is (= ["KLGA" "INFO" "A"]
           (split-atis "KLGA INFO A" 4)))))
