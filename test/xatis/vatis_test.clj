(ns xatis.vatis-test
  (:require [clojure.test :refer :all]
            [xatis.vatis :refer :all]))

(def partial-data
  "<?xml version=\"1.0\"?>
<Facility>
  <ID>KLGA</ID>
  <Name>LaGuardia</Name>
  <IDS />
  <VoiceServer>rw.liveatc.net</VoiceServer>
  <AtisFrequency>127.050</AtisFrequency>
  <Profiles>
    <Profile>
      <Name>L ILS 22 D 31</Name>
      <DepArrNotice>ARRS EXPT ILS RWY 22 APPR. DEPS EXPT RWY 31.</DepArrNotice>
    </Profile>
  </Profiles>
</Facility>")

(deftest resolve-keyword-test
  (testing "Easy"
    (is (= :name (resolve-keyword :Name))))
  (testing "No Replacements"
    (is (= :dep-arr-notice (resolve-keyword :DepArrNotice))))
  (testing "One Replacement"
    (is (= :vfr-direction (resolve-keyword :VFRDirection)))
    (is (= :verify-sid (resolve-keyword :VerifySID)))
    (is (= :notam (resolve-keyword :NOTAM))))
  (testing "Multi-Replacement"
    (is (= :ils-approach (resolve-keyword :ILSApch)))))

(deftest read-vatis-test
  (testing "Read partial vatis from string"
    (let [result (read-string partial-data)
          profiles (:profiles result)]
      (is (= "KLGA" (:id result)))
      (is (= "LaGuardia" (:facility result)))
      (is (= "rw.liveatc.net" (:server result)))
      (is (= "127.050" (:frequency result)))
      (is (= 1 (count profiles)))
      (is (= "L ILS 22 D 31" (:name (first profiles)))))))
