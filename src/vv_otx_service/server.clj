(ns vv-otx-service.server
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [vv-otx-service.routes :as routes]
            [vv-otx-service.db :as db]
            [ring.util.response :as ring-resp]))

(defn home-page
  [request]
  (ring-resp/response "Hello Centripetal Networks"))

(def common-interceptors [(body-params/body-params) http/html-body])

(defn all-routes [db]
  (clojure.set/union
   #{["/" :get (conj common-interceptors `home-page)]}
   (routes/routes db)))

(defrecord WebServer [port db server]
  component/Lifecycle
  (start [this]
    (println "Starting WebServer component")
    (println "Available routes:")
    (doseq [route (all-routes db)]
      (println "  " route))
    (let [service-map {:env :prod
                       ::http/routes (all-routes db)
                       ::http/resource-path "/public"
                       ::http/type :jetty
                       ::http/host "0.0.0.0"
                       ::http/port port
                       ::http/join? false
                       ::http/container-options {:h2c? true
                                                 :h2? false
                                                 :ssl? false}}
          server (-> service-map
                     http/create-server
                     http/start)]
      (println "Server started on port" port)
      (assoc this :server server)))
  (stop [this]
    (println "Stopping WebServer component")
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