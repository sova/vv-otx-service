(ns vv-otx-service.routes
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [vv-otx-service.db :as db]
            [clojure.data.json :as json]
            [clojure.string :as str]))

(defn json-response
  "Create a JSON response with proper content-type header"
  ([data] (json-response 200 data))
  ([status data]
   {:status status
    :headers {"Content-Type" "application/json"}
    :body (json/write-str data)}))

(defn db-interceptor [db]
  {:name ::db-injector
   :enter (fn [context]
            (assoc-in context [:request :components :db] db))})

(defn search-indicators-handler [request]
  (let [criteria (or (:json-params request) (:params request) {})
        db (get-in request [:components :db])]
    (try
      (let [indicators (db/search-indicators db criteria)]
        (json-response indicators))
      (catch Exception e
        (json-response 500 {:error (.getMessage e)})))))

(defn indicator-handler [request]
  (let [id (get-in request [:path-params :id])
        db (get-in request [:components :db])
        indicator (db/get-by-id db id)]
    (if indicator
      (json-response indicator)
      (json-response 404 {:error "Indicator not found"}))))

(defn get-all-indicators [request]
  (let [db (get-in request [:components :db])
        indicators (db/get-all db)]
    (json-response indicators)))

(defn get-indicators-by-type [request]
  (let [type (get-in request [:query-params :type])
        db (get-in request [:components :db])
        indicators (db/get-by-type db type)]
    (json-response indicators)))

(defn routes [db]
  #{["/indicators/search" :post [(body-params/body-params) (db-interceptor db) search-indicators-handler] :route-name :search-indicators]
    ["/indicators/search" :options [(db-interceptor db) (fn [_] {:status 200 :body "" :headers {}})] :route-name :search-indicators-options]
    ["/indicators/:id" :get [(db-interceptor db) indicator-handler] :route-name :get-indicator]
    ["/indicators" :get [(db-interceptor db)
                         (fn [request]
                           (if (get-in request [:query-params :type])
                             (get-indicators-by-type request)
                             (get-all-indicators request)))]
     :route-name :get-indicators]
    ["/indicators" :options [(db-interceptor db) (fn [_] {:status 200 :body "" :headers {}})] :route-name :get-indicators-options]})