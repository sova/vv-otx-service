(ns vv-otx-service.server
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.cors :as cors]
            [vv-otx-service.routes :as routes]
            [vv-otx-service.db :as db]
            [ring.util.response :as ring-resp]
            [clojure.tools.logging :as log]))

(defn home-page
  [request]
  (log/info "Home page requested")
  (ring-resp/response "Hello Centripetal Networks"))

;; Enhanced CORS interceptor
(def cors-interceptor
  (cors/allow-origin {:allowed-origins ["http://localhost:3001"]
                      :creds true
                      :max-age 3600}))

;; Logging interceptor
(def logging-interceptor
  {:name :logging
   :enter (fn [context]
            (let [request (:request context)]
              (log/info "Request received:"
                        {:method (:request-method request)
                         :uri (:uri request)
                         :headers (select-keys (:headers request)
                                               ["origin" "content-type" "accept"])
                         :body-params (:body-params request)})
              context))
   :leave (fn [context]
            (let [response (:response context)]
              (log/info "Response sent:"
                        {:status (:status response)
                         :headers (select-keys (:headers response)
                                               ["content-type" "access-control-allow-origin"])})
              context))})

(def common-interceptors
  [cors-interceptor
   logging-interceptor
   (body-params/body-params)
   http/html-body])

(defn all-routes [db]
  (route/expand-routes
   (clojure.set/union
    #{["/" :get (conj common-interceptors `home-page)]}
    (routes/routes db))))

(defrecord WebServer [port db server]
  component/Lifecycle
  (start [this]
    (log/info "Starting WebServer component")
    (try
      (log/info "Available routes:")
      (doseq [route (all-routes db)]
        (log/info "  " route))
      (catch Exception e
        (log/error "Error loading routes:" (.getMessage e))))
    (let [service-map {::http/routes (all-routes db)
                       ::http/resource-path "/public"
                       ::http/type :jetty
                       ::http/host "0.0.0.0"
                       ::http/port port
                       ::http/join? false
                       ;; Enable CORS at the service level
                       ::http/allowed-origins {:creds true
                                               :allowed-origins ["http://localhost:3001"]}
                       ::http/container-options {:h2c? true
                                                 :h2? false
                                                 :ssl? false}}
          server (-> service-map
                     http/create-server
                     http/start)]
      (log/info "Server started on port" port)
      (log/info "CORS enabled for: http://localhost:3001")
      (assoc this :server server)))
  (stop [this]
    (log/info "Stopping WebServer component")
    (when server
      (http/stop server))
    (assoc this :server nil)))

(defn new-web-server [port]
  (map->WebServer {:port port}))

(defn system [config]
  (component/system-map
   :db (db/new-database (:db-file config))
   :web-server (component/using
                (new-web-server (:port config))
                [:db])))