(ns vv-otx-service.db
  (:require [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn build-indexes
  "Build indexes for fast lookups"
  [data]
  (let [all-indicators (mapcat :indicators data)
        id-index (into {} (map (juxt :id identity) all-indicators))
        type-index (group-by :type all-indicators)]
    {:id-index id-index
     :type-index type-index
     :all-indicators all-indicators}))

(defn normalize-string
  "Normalize string for case-insensitive searching"
  [s]
  (when s (str/lower-case (str s))))

(defn matches-criteria?
  "Check if an indicator matches the search criteria"
  [indicator entry criteria]
  (every?
   (fn [[k v]]
     (case k
       :indicator (= (:indicator indicator) v)
       :description (when-let [desc (:description indicator)]
                     (str/includes? (normalize-string desc) (normalize-string v)))
       :created (= (:created indicator) v)
       :title (when-let [title (:title indicator)]
               (str/includes? (normalize-string title) (normalize-string v)))
       :content (when-let [content (:content indicator)]
                 (str/includes? (normalize-string content) (normalize-string v)))
       :type (= (:type indicator) v)
       :id (= (:id indicator) (if (string? v) (Long/parseLong v) v))
       :author (when-let [author (:author_name entry)]
                (str/includes? (normalize-string author) (normalize-string v)))
       true))  ; Ignore unsupported keys
   criteria))

(defrecord Database [file-path data indexes]
  component/Lifecycle
  (start [this]
    (println "Starting Database component")
    (try
      (let [resource (io/resource file-path)]
        (if resource
          (let [json-data (json/read-str (slurp resource) :key-fn keyword)
                indexes (build-indexes json-data)]
            (println "Database loaded successfully from" file-path)
            (println "Built indexes for" (count (:all-indicators indexes)) "indicators")
            (assoc this :data json-data :indexes indexes))
          (throw (ex-info "Resource file not found" {:file-path file-path}))))
      (catch Exception e
        (println "Failed to load database:" (.getMessage e))
        (throw e))))
  (stop [this]
    (println "Stopping Database component")
    (assoc this :data nil :indexes nil)))

(defn new-database [file-path]
  (map->Database {:file-path file-path}))

(defn get-by-id
  "Fast O(1) lookup by ID using index"
  [db id]
  (let [id-num (if (string? id) (Long/parseLong id) id)]
    (get-in db [:indexes :id-index id-num])))

(defn get-all
  "Return all indicators (flattened from the original nested structure)"
  [db]
  (get-in db [:indexes :all-indicators]))

(defn get-by-type
  "Fast O(1) lookup by type using index"
  [db type]
  (get-in db [:indexes :type-index type] []))

(defn search-indicators
  "Search indicators with optimized filtering"
  [db criteria]
  (if (empty? criteria)
    []
    (let [indicators (get-in db [:indexes :all-indicators])
          entry-map (into {} (for [entry (:data db)
                                   indicator (:indicators entry)]
                               [(:id indicator) entry]))]
      ;; Fast path for single ID lookup
      (if (and (= 1 (count criteria)) (:id criteria))
        (if-let [indicator (get-by-id db (:id criteria))]
          [indicator]
          [])
        ;; Fast path for single type lookup
        (if (and (= 1 (count criteria)) (:type criteria))
          (get-by-type db (:type criteria))
          ;; General search - still O(n) but with better structure
          (filter (fn [indicator]
                    (let [entry (get entry-map (:id indicator))]
                      (matches-criteria? indicator entry criteria)))
                  indicators))))))