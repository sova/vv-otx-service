(ns vv-otx-service.server
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.cors :as cors]
            [vv-otx-service.routes :as routes]
            [vv-otx-service.db :as db]
            [ring.util.response :as ring-resp]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]))

(defn home-page
  [request]
  (log/info "Home page requested")
  (let [index-file (io/file "public/index.html")]
    (if (.exists index-file)
      (do
        (log/info "Serving index.html from public/")
        {:status 200
         :body (slurp index-file)
         :headers {"Content-Type" "text/html; charset=utf-8"
                   "Content-Security-Policy" "script-src 'self' http://localhost:8080"
                   "Cache-Control" "no-cache"}})
      (do
        (log/error "Failed to find public/index.html")
        {:status 404
         :body "Index.html not found"
         :headers {"Content-Type" "text/html; charset=utf-8"}}))))

;; Static file handler
(defn static-file-handler
  [request]
  (let [uri (:uri request)
        file-path (str "public" uri)
        file (io/file file-path)]
    (if (.exists file)
      (let [content-type (cond
                           (.endsWith uri ".js") "application/javascript"
                           (.endsWith uri ".css") "text/css"
                           (.endsWith uri ".html") "text/html"
                           :else "application/octet-stream")]
        (log/info "Serving static file:" file-path)
        {:status 200
         :body (slurp file)
         :headers {"Content-Type" content-type
                   "Cache-Control" "no-cache"}})
      (do
        (log/warn "Static file not found:" file-path)
        {:status 404
         :body "File not found"
         :headers {"Content-Type" "text/plain"}}))))

(def cors-interceptor
  (cors/allow-origin {:allowed-origins ["http://localhost:3001" "http://localhost:8080" "http://127.0.0.1:8080"]
                      :creds true
                      :max-age 3600}))

;; Error handling interceptor
(def error-interceptor
  {:name :error-handler
   :error (fn [context exception]
            (log/error "Error in interceptor chain:" (.getMessage exception))
            (log/error "Exception details:" exception)
            (assoc context :response
                   {:status 500
                    :body "Internal Server Error"
                    :headers {"Content-Type" "text/plain"}}))})

;; Response validation interceptor
(def response-validation-interceptor
  {:name :response-validation
   :leave (fn [context]
            (let [response (:response context)]
              (cond
                (nil? response)
                (do
                  (log/error "Handler returned nil response")
                  (assoc context :response
                         {:status 500
                          :body "Handler returned nil response"
                          :headers {"Content-Type" "text/plain"}}))

                (not (map? response))
                (do
                  (log/error "Handler returned non-map response:" response)
                  (assoc context :response
                         {:status 500
                          :body "Handler returned invalid response"
                          :headers {"Content-Type" "text/plain"}}))

                (not (and (:status response) (pos-int? (:status response))))
                (do
                  (log/error "Handler returned response with invalid status:" response)
                  (assoc context :response
                         {:status 500
                          :body "Handler returned invalid status"
                          :headers {"Content-Type" "text/plain"}}))

                :else
                context)))})

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
  [error-interceptor
   cors-interceptor
   logging-interceptor
   (body-params/body-params)
   http/html-body
   response-validation-interceptor])

(defn all-routes [db]
  (try
    (let [api-routes (routes/routes db)
          home-routes #{["/" :get (conj common-interceptors `home-page) :route-name :home]
                        ["/js/*path" :get (conj common-interceptors `static-file-handler) :route-name :js-files]
                        ["/css/*path" :get (conj common-interceptors `static-file-handler) :route-name :css-files]
                        ["/assets/*path" :get (conj common-interceptors `static-file-handler) :route-name :asset-files]}]
      (log/info "API routes:" api-routes)
      (route/expand-routes
       (clojure.set/union home-routes api-routes)))
    (catch Exception e
      (log/error "Error creating routes:" (.getMessage e))
      (log/error "Exception details:" e)
      ;; Return a minimal route set if routes fail to load
      (route/expand-routes
       #{["/" :get (conj common-interceptors `home-page) :route-name :home]
         ["/js/*path" :get (conj common-interceptors `static-file-handler) :route-name :js-files]
         ["/health" :get (conj common-interceptors
                               (fn [_] {:status 200
                                        :body "OK"
                                        :headers {"Content-Type" "text/plain"}})) :route-name :health]}))))

(defrecord WebServer [port db server]
  component/Lifecycle
  (start [this]
    (log/info "Starting WebServer component")
    (let [service-map {::http/routes (all-routes db)
                       ::http/resource-path "/public"
                       ::http/type :jetty
                       ::http/host "0.0.0.0"
                       ::http/port port
                       ::http/join? false
                       ::http/allowed-origins {:creds true
                                               :allowed-origins ["http://localhost:3001" "http://localhost:8080" "http://127.0.0.1:8080"]}
                       ::http/enable-session {}
                       ::http/container-options {:h2c? true
                                                 :h2? false
                                                 :ssl? false}}
          server (-> service-map
                     http/create-server
                     http/start)]
      (log/info "Server started on port" port)
      (log/info "CORS enabled for: http://localhost:3001, http://localhost:8080, http://127.0.0.1:8080")
      (log/info "Static files served from: /public")
      (log/info "Available routes:")
      (doseq [route (all-routes db)]
        (log/info "  " route))
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