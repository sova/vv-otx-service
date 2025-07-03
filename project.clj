(defproject vv-otx-service "0.0.8"
  :description "VV OTX Service for Centripetal Networks"
  :url ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [io.pedestal/pedestal.service "0.8.0-beta-1"]
                 [io.pedestal/pedestal.route "0.8.0-beta-1"]
                 [io.pedestal/pedestal.jetty "0.8.0-beta-1"]
                 [ring/ring-core "1.12.2"]
                 [ring/ring-codec "1.2.0"]
                 [com.stuartsierra/component "1.1.0"]
                 [org.clojure/data.json "2.5.0"]
                 [org.clojure/test.check "1.1.1"]
                 [ring/ring-mock "0.4.0"]
                 [ch.qos.logback/logback-classic "1.2.10" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.35"]
                 [org.slf4j/jcl-over-slf4j "1.7.35"]
                 [org.slf4j/log4j-over-slf4j "1.7.35"]
                 [org.clojure/tools.logging "1.3.0"]
                 [ring-cors/ring-cors "0.1.13"]
                 [reagent "1.2.0"]
                 [thheller/shadow-cljs "3.1.7"]]
  :min-lein-version "2.0.0"
  :source-paths ["src" "cljs-src"]
  :resource-paths ["config" "resources" "test/resources" "public"] 
  :profiles {:dev {:aliases {"run-dev" ["do"
                                        ["trampoline" "run" "-m" "vv-otx-service.server/run-dev"]
                                        ["shadow" "watch" "app"]]}
                   :dependencies [[io.pedestal/pedestal.service-tools "0.7.2"]
                                  [thheller/shadow-cljs "2.28.8"]]}
             :uberjar {:aot :all}}
  :main vv-otx-service.core
  :uberjar-name "vv-otx-service-0.0.8-standalone.jar")