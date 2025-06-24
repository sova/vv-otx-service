(ns vv-otx-service.routes-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [clojure.data.json :as json]
            [vv-otx-service.routes :as routes]
            [vv-otx-service.db :as db]
            [com.stuartsierra.component :as component]
            [io.pedestal.http.route :as route]
            [io.pedestal.http :as http]
            [io.pedestal.test :as test]))

;; Test data matching your test-indicators.json structure
(def test-data
  [{:indicators [{:indicator "85.93.20.243"
                  :description ""
                  :created "2018-07-09T18:02:40"
                  :title ""
                  :content ""
                  :type "IPv4"
                  :id 460576}
                 {:indicator "221.194.44.211"
                  :description "On: 2018-07-09T17:53:05.541000OS detected: Linux 3.11 and newer"
                  :created "2018-07-09T18:02:40"
                  :title ""
                  :content ""
                  :type "IPv4"
                  :id 671506}]
    :author_name "marcoramilli"
    :created "2018-07-09T18:02:39.394000"
    :modified "2018-07-09T18:02:39.394000"
    :public 1
    :tags ["suricata" "cowrie"]
    :tlp "green"}])

(defn create-test-db []
  (let [db (db/map->Database {:file-path "test-indicators.json"
                              :data test-data
                              :indexes (db/build-indexes test-data)})]
    db))

(defn parse-json-response [response]
  (when (:body response)
    (json/read-str (:body response) :key-fn keyword)))

(deftest test-json-response
  (testing "json-response function"
    (let [data {:test "value"}
          response (routes/json-response data)]
      (is (= 200 (:status response)))
      (is (= "application/json" (get-in response [:headers "Content-Type"])))
      (is (= "{\"test\":\"value\"}" (:body response))))

    (let [data {:error "test error"}
          response (routes/json-response 500 data)]
      (is (= 500 (:status response)))
      (is (= "application/json" (get-in response [:headers "Content-Type"])))
      (is (= "{\"error\":\"test error\"}" (:body response))))))

(deftest test-get-all-indicators
  (testing "get-all-indicators handler"
    (let [db (create-test-db)
          request {:components {:db db}}
          response (routes/get-all-indicators request)
          parsed-body (parse-json-response response)]
      (is (= 200 (:status response)))
      (is (= "application/json" (get-in response [:headers "Content-Type"])))
      (is (= 2 (count parsed-body)))
      (is (some #(= "85.93.20.243" (:indicator %)) parsed-body))
      (is (some #(= "221.194.44.211" (:indicator %)) parsed-body)))))

(deftest test-indicator-handler
  (testing "indicator-handler with valid ID"
    (let [db (create-test-db)
          request {:path-params {:id "460576"}
                   :components {:db db}}
          response (routes/indicator-handler request)
          parsed-body (parse-json-response response)]
      (is (= 200 (:status response)))
      (is (= "85.93.20.243" (:indicator parsed-body)))
      (is (= 460576 (:id parsed-body)))))

  (testing "indicator-handler with invalid ID"
    (let [db (create-test-db)
          request {:path-params {:id "999999"}
                   :components {:db db}}
          response (routes/indicator-handler request)
          parsed-body (parse-json-response response)]
      (is (= 404 (:status response)))
      (is (= "Indicator not found" (:error parsed-body))))))

(deftest test-get-indicators-by-type
  (testing "get-indicators-by-type with valid type"
    (let [db (create-test-db)
          request {:query-params {:type "IPv4"}
                   :components {:db db}}
          response (routes/get-indicators-by-type request)
          parsed-body (parse-json-response response)]
      (is (= 200 (:status response)))
      (is (= 2 (count parsed-body)))
      (is (every? #(= "IPv4" (:type %)) parsed-body))))

  (testing "get-indicators-by-type with invalid type"
    (let [db (create-test-db)
          request {:query-params {:type "Domain"}
                   :components {:db db}}
          response (routes/get-indicators-by-type request)
          parsed-body (parse-json-response response)]
      (is (= 200 (:status response)))
      (is (= 0 (count parsed-body))))))

(deftest test-search-indicators-handler
  (testing "search-indicators-handler with ID criteria"
    (let [db (create-test-db)
          request {:json-params {:id 460576}
                   :components {:db db}}
          response (routes/search-indicators-handler request)
          parsed-body (parse-json-response response)]
      (is (= 200 (:status response)))
      (is (= 1 (count parsed-body)))
      (is (= "85.93.20.243" (:indicator (first parsed-body))))))

  (testing "search-indicators-handler with type criteria"
    (let [db (create-test-db)
          request {:json-params {:type "IPv4"}
                   :components {:db db}}
          response (routes/search-indicators-handler request)
          parsed-body (parse-json-response response)]
      (is (= 200 (:status response)))
      (is (= 2 (count parsed-body)))
      (is (every? #(= "IPv4" (:type %)) parsed-body))))

  (testing "search-indicators-handler with indicator criteria"
    (let [db (create-test-db)
          request {:json-params {:indicator "85.93.20.243"}
                   :components {:db db}}
          response (routes/search-indicators-handler request)
          parsed-body (parse-json-response response)]
      (is (= 200 (:status response)))
      (is (= 1 (count parsed-body)))
      (is (= "85.93.20.243" (:indicator (first parsed-body))))))

  (testing "search-indicators-handler with description criteria"
    (let [db (create-test-db)
          request {:json-params {:description "OS detected"}
                   :components {:db db}}
          response (routes/search-indicators-handler request)
          parsed-body (parse-json-response response)]
      (is (= 200 (:status response)))
      (is (= 1 (count parsed-body)))
      (is (= 671506 (:id (first parsed-body)))))

    ;; Test case-insensitive matching
    (let [db (create-test-db)
          request {:json-params {:description "linux"}
                   :components {:db db}}
          response (routes/search-indicators-handler request)
          parsed-body (parse-json-response response)]
      (is (= 200 (:status response)))
      (is (= 1 (count parsed-body)))
      (is (= 671506 (:id (first parsed-body))))))

  (testing "search-indicators-handler with no criteria"
    (let [db (create-test-db)
          request {:json-params {}
                   :components {:db db}}
          response (routes/search-indicators-handler request)
          parsed-body (parse-json-response response)]
      (is (= 200 (:status response)))
      (is (= 0 (count parsed-body)))))

  (testing "search-indicators-handler with params fallback"
    (let [db (create-test-db)
          request {:params {:type "IPv4"}
                   :components {:db db}}
          response (routes/search-indicators-handler request)
          parsed-body (parse-json-response response)]
      (is (= 200 (:status response)))
      (is (= 2 (count parsed-body)))))

  (testing "search-indicators-handler with multiple criteria"
    (let [db (create-test-db)
          request {:json-params {:type "IPv4" :id 460576}
                   :components {:db db}}
          response (routes/search-indicators-handler request)
          parsed-body (parse-json-response response)]
      (is (= 200 (:status response)))
      (is (= 1 (count parsed-body)))
      (is (= "85.93.20.243" (:indicator (first parsed-body))))))

  (testing "search-indicators-handler with no matching criteria"
    (let [db (create-test-db)
          request {:json-params {:indicator "nonexistent"}
                   :components {:db db}}
          response (routes/search-indicators-handler request)
          parsed-body (parse-json-response response)]
      (is (= 200 (:status response)))
      (is (= 0 (count parsed-body))))))

(deftest test-db-interceptor
  (testing "db-interceptor injects database into request"
    (let [db (create-test-db)
          interceptor (routes/db-interceptor db)
          context {:request {}}
          result ((:enter interceptor) context)]
      (is (= db (get-in result [:request :components :db]))))))

(deftest test-routes-structure
  (testing "routes function returns correct route structure"
    (let [db (create-test-db)
          route-set (routes/routes db)]
      (is (set? route-set))
      (is (= 3 (count route-set)))

      ;; Check that all expected routes are present
      (let [route-paths (map first route-set)]
        (is (some #(= "/indicators/search" %) route-paths))
        (is (some #(= "/indicators/:id" %) route-paths))
        (is (some #(= "/indicators" %) route-paths)))

      ;; Check HTTP methods
      (let [route-methods (map second route-set)]
        (is (some #(= :post %) route-methods))
        (is (some #(= :get %) route-methods))))))

;; Error handling tests
(deftest test-error-handling
  (testing "search-indicators-handler handles database errors gracefully"
    ;; Create a mock database that will throw an exception
    (let [broken-db {:indexes {:all-indicators nil}}
          request {:json-params {:type "IPv4"}
                   :components {:db broken-db}}]
      ;; Stub the db/search-indicators function to throw an exception
      (with-redefs [db/search-indicators (fn [_ _] (throw (Exception. "Database connection failed")))]
        (let [response (routes/search-indicators-handler request)
              parsed-body (parse-json-response response)]
          (is (= 500 (:status response)))
          (is (contains? parsed-body :error))
          (is (= "Database connection failed" (:error parsed-body))))))))