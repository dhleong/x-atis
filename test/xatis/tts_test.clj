(ns xatis.tts-test
  (:require [clojure.test :refer :all]
            [xatis.tts :refer :all]))

(deftest process-text-test
  (testing "Expand some acronyms"
    (is (= "I L S" (process-text "ILS"))))
  (testing "Fix some incorrect pronunciations"
    (is (= "REED BACK ALL HS" (process-text "READBACK ALL HS")))))
