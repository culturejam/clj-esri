(ns clj-esri.core
  (:require [clj-json.core :as json]
            [clojure.set :as set]
            [clojure.string :as string]
            [clj-http.client :as client]))


(declare status-handler)

(def ^:dynamic *protocol* "http")
(def ^:dynamic *access-token* nil)
(def ^:dynamic *referer* "http://www.arcgis.com/")
(def ^:dynamic *arcgis-online-endpoint* "www.arcgis.com")
(def ^:dynamic *arcgis-server-endpoint* "sampleserver1.arcgisonline.com")



(defmacro with-https
  [ & body]
  `(binding [*protocol* "https"]
     (do
       ~@body)))


;; Some Esri endpoints require a referer. The value doesn't actually matter,
;; so you shouldn't need to set this in practice.
;; Referer must match in request for token and later calls.
(defmacro with-referer
  [referer & body]
  `(binding [*referer* referer]
     (do
       ~@body)))


(defmacro with-token
  "Set the secret access token to be used for all contained Esri requests."
  [access-token & body]
  `(binding [*access-token* ~access-token]
     (do
       ~@body)))


(defmacro with-arcgis-config
  [arcgis-online arcgis-server & body]
  `(binding [*arcgis-online-endpoint* ~arcgis-online
             *arcgis-server-endpoint* ~arcgis-server]
     (do
       ~@body)))


(defn build-service-endpoint
  [service-type]
  (case service-type
    :arcgis-online (str *protocol* "://" *arcgis-online-endpoint*)
    :arcgis-server (str *protocol* "://" *arcgis-server-endpoint*)))


(defn raw-handler
  [result]
  result)

(defn status-handler
  "Handle the various HTTP status codes that may be returned when accessing the Esri API."
  [result]
  (condp #(if (coll? %1)
            (first (filter (fn [x] (== x %2)) %1))
            (== %2 %1)) (:code result)
    200 result
    304 nil
    [400 401 403 404 406 500 502 503]
    (let [body (:content result)
          headers (into {} (:headers result))
          error-msg (:error body)
          error-code (:code result)
          request-uri (:request body)]
      (throw (proxy [Exception]
                 [(str "[" error-code "] " error-msg ". [" request-uri "]")]
               (request [] (body "request")))))))


(defn expand-uri
  [uri params]
    (if (empty? params)
      uri
      (let [m     (first params)
            token (str "::" (name (key m)) "::")]
        (expand-uri (clojure.string/replace uri token (val m))
                    (apply dissoc params m)))))


(defmacro def-esri-method
  [method-name service-type req-method req-url required-params optional-params handler]

  (let [required-fn-params (vec (sort (map #(symbol (name %))
                                           required-params)))
        optional-fn-params (vec (sort (map #(symbol (name %))
                                           optional-params)))]
    `(defn ~method-name
       [~@required-fn-params & rest#]
       (let [rest-map# (apply hash-map rest#)
             provided-optional-params# (set/intersection (set ~optional-params)
                                                         (set (keys rest-map#)))
             required-query-param-names#
             (map (fn [x#] (keyword (string/replace (name x#) #"-" "_" )))
                  ~required-params)
             optional-query-param-names-mapping#
             (map (fn [x#]
                    [x# (keyword (string/replace (name x#) #"-" "_"))])
                  provided-optional-params#)
             required-hash-map# (apply hash-map
                                       (vec (interleave
                                             required-query-param-names#
                                             ~required-fn-params)))
             optional-hash-map# (apply merge
                                        (map (fn [x#] {(second x#)
                                                       ((first x#)
                                                        rest-map#)})
                                             optional-query-param-names-mapping#))
             query-params# (merge required-hash-map# optional-hash-map#
                                  {:f "pjson" :referer *referer*}
                                  (if *access-token*
                                    {:token *access-token*}))
             req-uri# (str (build-service-endpoint ~service-type) (expand-uri ~req-url required-hash-map#))
             ]
         (~handler (~(symbol "client" (name req-method))
                    req-uri#
                    {:query-params query-params#
                     :headers {"Referer" *referer*}
                     :client-params {"http.useragent" "clj-esri"}
                     :as :json}
                    ))))))


;;Define Esri methods

;Get request token for user
;http://resources.arcgis.com/en/help/arcgis-rest-api/index.html#/Generate_Token/02r3000000m5000000/
(def-esri-method generate-token
  :arcgis-online
  :post
  "/sharing/rest/generatetoken"
  [:username :password :client]
  [:ip :expiration]
  (comp :body raw-handler))


;Get request token for server application
(def-esri-method get-app-request-token
  :arcgis-online
  :post
  "/sharing/rest/oauth2/token"
  [:client_id :client_secret :grant_type]
  []
  (comp :body raw-handler))


(def-esri-method is-service-name-available
  :arcgis-online
  :post
  "/sharing/rest/portals/::user::/isServiceNameAvailable"
  [:user :name :type]
  []
  (comp :body raw-handler))



(def-esri-method get-services
  :arcgis-server
  :get
  "/arcgis/rest/services"
  []
  []
  (comp :body raw-handler))


;Gets information about a FeatureService.
;See: http://resources.arcgis.com/en/help/arcgis-rest-api/index.html#/Feature_Service/02r3000000z2000000/
(def-esri-method get-feature-service-info
  :arcgis-server
  :get
  "/arcgis/rest/services/::name::/FeatureServer"
  [:name]
  []
  (comp :body raw-handler))


(def-esri-method create-feature-service
  :arcgis-online
  :post
  "/sharing/rest/content/users/::user::/createService"
  [:user :targettype :createparameters]
  []
  (comp :body raw-handler))


;Get a FeatureService status
;See: http://services.arcgis.com/help/statusFeatureService.html
(def-esri-method feature-service-status
  :arcgis-server
  :post
  "/arcgis/admin/services/::name::.FeatureServer/status"
  [:name]
  []
  (comp :body raw-handler))


;Refresh a FeatureService
;See: http://services.arcgis.com/help/refreshFeatureService.html
(def-esri-method feature-service-refresh
  :arcgis-server
  :post
  "/arcgis/admin/services/::name::.FeatureServer/refresh"
  [:name]
  []
  (comp :body raw-handler))


;Modify a FeatureService
;See: http://services.arcgis.com/help/layerAddToDefinition.html
(def-esri-method add-to-definition
  :arcgis-server
  :post
  "/arcgis/admin/services/::name::.FeatureServer/addToDefinition"
  [:name :addToDefinition]
  []
  (comp :body raw-handler))


;Adds new features to a FeatureService
;See: http://resources.arcgis.com/en/help/arcgis-rest-api/index.html#/Add_Features/02r30000010m000000/
(def-esri-method add-features
  :arcgis-server
  :post
  "/arcgis/rest/services/::service_name::/FeatureServer/::layer_id::/addFeatures"
  [:service_name :layer_id :features]
  [:gdbversion :rollbackonfailure]
  (comp :body raw-handler))
