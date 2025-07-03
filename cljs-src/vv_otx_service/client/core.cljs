(ns vv-otx-service.client.core
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdom]))

;; State for form inputs and results
(def search-criteria (r/atom {:indicator "" :id "" :type "" :description ""}))
(def search-results (r/atom []))
(def search-loading (r/atom false))
(def search-error (r/atom nil))

;; Root element for React 18
(defonce root (atom nil))

;; Logging helper
(defn log [level message & args]
  (let [timestamp (.toISOString (js/Date.))
        formatted-message (str "[" timestamp "] " (name level) ": " message)]
    (case level
      :debug (js/console.debug formatted-message (clj->js args))
      :info (js/console.info formatted-message (clj->js args))
      :warn (js/console.warn formatted-message (clj->js args))
      :error (js/console.error formatted-message (clj->js args))
      (js/console.log formatted-message (clj->js args)))))

;; Function to send POST request to backend
(defn search-indicators []
  (log :info "Starting search with criteria:" @search-criteria)
  (reset! search-loading true)
  (reset! search-error nil)
  (reset! search-results [])

  ;; Filter out empty values from search criteria
  (let [filtered-criteria (into {} (filter (fn [[k v]]
                                             (and v (not= v "")))
                                           @search-criteria))
        request-body (clj->js filtered-criteria)
        request-options #js {:method "POST"
                             :headers #js {"Content-Type" "application/json"
                                           "Accept" "application/json"}
                             :body (js/JSON.stringify request-body)}]

    (log :debug "Filtered criteria:" filtered-criteria)
    (log :debug "Request options:" request-options)
    (log :debug "Request body JSON:" (js/JSON.stringify request-body))

    (-> (js/fetch "http://localhost:8080/indicators/search" request-options)
        (.then (fn [response]
                 (log :debug "Received response:" response)
                 (log :debug "Response status:" (.-status response))
                 (log :debug "Response ok:" (.-ok response))
                 (log :debug "Response status text:" (.-statusText response))

                 ;; Log response headers
                 (let [headers (.-headers response)]
                   (log :debug "Response headers:")
                   (.forEach headers (fn [value key]
                                       (log :debug "  " key ":" value))))

                 (if (.-ok response)
                   (do
                     (log :info "Request successful, parsing JSON")
                     (.then (.json response)
                            (fn [data]
                              (log :debug "Raw response data:" data)
                              (log :debug "Response data type:" (type data))
                              (let [parsed-data (js->clj data :keywordize-keys true)]
                                (log :info "Parsed response data:" parsed-data)
                                (log :info "Number of results:" (count parsed-data))
                                (log :debug "First result (if any):" (first parsed-data))
                                (reset! search-results parsed-data)
                                (reset! search-loading false)))))
                   (do
                     (log :error "Request failed with status:" (.-status response))
                     (.then (.text response)
                            (fn [error-text]
                              (log :error "Error response body:" error-text)
                              (reset! search-error (str "Server error: " (.-status response) " - " error-text))
                              (reset! search-loading false)))))))
        (.catch (fn [error]
                  (log :error "Network error occurred:" error)
                  (log :error "Error details:" (.-message error))
                  (log :error "Error stack:" (.-stack error))
                  (reset! search-error (str "Network error: " (.-message error)))
                  (reset! search-loading false))))))

;; Test function to get all indicators
(defn get-all-indicators []
  (log :info "Getting all indicators")
  (reset! search-loading true)
  (reset! search-error nil)

  (-> (js/fetch "http://localhost:8080/indicators"
                #js {:method "GET"
                     :headers #js {"Accept" "application/json"}})
      (.then (fn [response]
               (log :debug "Get all response status:" (.-status response))
               (if (.-ok response)
                 (.then (.json response)
                        (fn [data]
                          (let [parsed-data (js->clj data :keywordize-keys true)]
                            (log :info "Retrieved all indicators count:" (count parsed-data))
                            (log :debug "First indicator:" (first parsed-data))
                            (reset! search-results parsed-data)
                            (reset! search-loading false))))
                 (do
                   (log :error "Failed to get all indicators")
                   (.then (.text response)
                          (fn [error-text]
                            (reset! search-error (str "Failed to get indicators: " error-text))
                            (reset! search-loading false)))))))
      (.catch (fn [error]
                (log :error "Network error getting all indicators:" error)
                (reset! search-error (str "Network error: " (.-message error)))
                (reset! search-loading false)))))

;; Input change handler with logging
(defn handle-input-change [field value]
  (log :debug "Input changed for field:" field "new value:" value)
  (swap! search-criteria assoc field value)
  (log :debug "Updated search criteria:" @search-criteria))

;; Main UI component
(defn app []
  [:section.section
   [:div.container
    [:h1.title "VV OTX Service Search"]

    ;; Debug info
    [:div.notification.is-info.is-light
     [:strong "Debug Info:"]
     [:br]
     "Search Criteria: " (pr-str @search-criteria)
     [:br]
     "Loading: " (pr-str @search-loading)
     [:br]
     "Results Count: " (count @search-results)]

    ;; Search Form
    [:div.field
     [:label.label "Indicator Value"]
     [:div.control
      [:input.input {:type "text"
                     :value (:indicator @search-criteria)
                     :placeholder "Enter indicator value (e.g., malicious IP, domain, hash)"
                     :on-change #(handle-input-change :indicator (-> % .-target .-value))}]]]

    [:div.field
     [:label.label "Indicator ID"]
     [:div.control
      [:input.input {:type "text"
                     :value (:id @search-criteria)
                     :placeholder "Enter indicator ID (e.g., 1, 2, 3)"
                     :on-change #(handle-input-change :id (-> % .-target .-value))}]]]

    [:div.field
     [:label.label "Type"]
     [:div.control
      [:input.input {:type "text"
                     :value (:type @search-criteria)
                     :placeholder "Enter type (e.g., IPv4, domain, FileHash-SHA256)"
                     :on-change #(handle-input-change :type (-> % .-target .-value))}]]]

    [:div.field
     [:label.label "Description"]
     [:div.control
      [:input.input {:type "text"
                     :value (:description @search-criteria)
                     :placeholder "Enter description keywords"
                     :on-change #(handle-input-change :description (-> % .-target .-value))}]]]

    [:div.field
     [:div.control
      [:button.button.is-primary
       {:on-click search-indicators
        :disabled @search-loading
        :class (when @search-loading "is-loading")}
       "Search"]
      [:button.button.is-info.ml-2
       {:on-click get-all-indicators
        :disabled @search-loading
        :class (when @search-loading "is-loading")}
       "Get All"]
      [:button.button.is-light.ml-2
       {:on-click #(do
                     (log :info "Clearing search criteria")
                     (reset! search-criteria {:indicator "" :id "" :type "" :description ""})
                     (reset! search-results [])
                     (reset! search-error nil))}
       "Clear"]]]

    ;; Error Display
    (when @search-error
      [:div.notification.is-danger.mt-3
       [:button.delete {:on-click #(reset! search-error nil)}]
       [:strong "Error: "] @search-error])

    ;; Loading indicator
    (when @search-loading
      [:div.mt-3
       [:progress.progress.is-primary "Loading..."]])

    ;; Results Display
    [:div.mt-5
     [:h2.subtitle "Results"]
     (cond
       @search-loading
       [:p "Searching..."]

       @search-error
       [:p "Search failed. Please try again."]

       (empty? @search-results)
       [:p "No results found. Try different search criteria or click 'Get All' to see all indicators."]

       :else
       [:div
        [:p.has-text-info (str "Found " (count @search-results) " result(s)")]
        [:table.table.is-striped.is-fullwidth
         [:thead
          [:tr
           [:th "ID"]
           [:th "Indicator"]
           [:th "Type"]
           [:th "Description"]
           [:th "Created"]
           [:th "Title"]]]
         [:tbody
          (for [result @search-results]
            [:tr {:key (:id result)}
             [:td (:id result)]
             [:td (:indicator result)]
             [:td (:type result)]
             [:td (:description result)]
             [:td (:created result)]
             [:td (:title result)]])]]])]]])

;; Initialize the app with React 18
(defn init []
  (log :info "Initializing VV OTX Service Client")
  (when-let [app-element (.getElementById js/document "app")]
    (if @root
      (do
        (log :debug "Updating existing root")
        (.render @root (r/as-element [app])))
      (do
        (log :debug "Creating new root")
        (let [new-root (rdom/create-root app-element)]
          (reset! root new-root)
          (.render new-root (r/as-element [app])))))))

;; Add some initial logging
(defn ^:export main []
  (log :info "VV OTX Service Client starting up")
  (init))