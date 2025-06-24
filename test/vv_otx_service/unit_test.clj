(ns vv-otx-service.unit-test
  (:require [clojure.test :refer :all]
            [vv-otx-service.db :as db]
            [vv-otx-service.routes :as routes]
            [clojure.data.json :as json]))

;; Unit tests for core business logic
(deftest test-normalize-string
  (testing "String normalization for search"
    (is (= "test" (db/normalize-string "TEST")))
    (is (= "test" (db/normalize-string "Test")))
    (is (nil? (db/normalize-string nil)))))

(deftest test-matches-criteria
  (testing "Search criteria matching logic"
    (let [indicator {:id 1 :type "IPv4" :indicator "192.168.1.1" :description "Test IP"}
          entry {:author_name "Test Author"}]
      (is (db/matches-criteria? indicator entry {:type "IPv4"}))
      (is (db/matches-criteria? indicator entry {:id 1}))
      (is (db/matches-criteria? indicator entry {:author "Test Author"}))
      (is (not (db/matches-criteria? indicator entry {:type "domain"}))))))

(deftest test-json-response
  (testing "JSON response formatting"
    (let [response (routes/json-response {:test "data"})]
      (is (= 200 (:status response)))
      (is (= "application/json" (get-in response [:headers "Content-Type"])))
      (is (= "{\"test\":\"data\"}" (:body response))))
    (let [response (routes/json-response 404 {:error "Not found"})]
      (is (= 404 (:status response))))))