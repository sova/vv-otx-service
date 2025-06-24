(ns vv-otx-service.core
  (:require [com.stuartsierra.component :as component]
            [vv-otx-service.server :as server])
  (:gen-class))

(defn -main [& args]
  (let [config {:port 8080 :db-file "indicators.json"}]
    (component/start (server/system config))))