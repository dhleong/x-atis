(ns xatis.networks.vatsim-test
  (:require [clojure.test :refer :all]
            [xatis.subscribe :as subrs]
            [xatis.networks.vatsim :refer :all]))

(deftest subscriptions-test
  (testing "Process Subscribe message"
    (let [subrs (subrs/create-subscriptions)
          sub-set (atom #{})
          sent (atom nil)]
      (on-message subrs
                  {:from "foo" :text "SUBscribe"}
                  #(swap! sent (constantly %2)))
      (subrs/each-sub subrs
        (swap! sub-set conj sub))
      (is (= #{"foo"} @sub-set))
      (is (= notify-subs-fmt @sent))))
  (testing "Process Unsubscribe message"
    (let [subrs (subrs/create-subscriptions)
          sub-set (atom #{})
          sent (atom nil)]
      (on-message subrs 
                  {:from "foo" :text "unSubScribe"}
                  #(swap! sent (constantly %2)))
      (subrs/each-sub subrs
        (swap! sub-set conj sub))
      (is (empty? @sub-set))
      (is (= notify-unsub-fmt @sent)))))
