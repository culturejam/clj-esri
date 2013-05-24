(ns clj-esri.core
  (:require [clj-json.core :as json]
            [clojure.set :as set]
            [clojure.string :as string]
            [com.twinql.clojure.http :as http]))


(declare status-handler)

(def ^:dynamic *protocol* "http")
(def ^:dynamic *access-token* nil)
(def ^:dynamic *arcgis-online-endpoint* "www.arcgis.com")
(def ^:dynamic *arcgis-server-endpoint* "sampleserver1.arcgisonline.com")


;; Get JSON from clj-apache-http
(defmethod http/entity-as :json [entity as state]
  (json/parse-string (http/entity-as entity :string state) true))


(defmacro with-https
  [ & body]
  `(binding [*protocol* "https"]
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
                                  {:f "pjson"}
                                  (if *access-token*
                                    {:token *access-token*}))
             req-uri# (str (build-service-endpoint ~service-type) (expand-uri ~req-url required-hash-map#))
             ]
         (~handler (~(symbol "http" (name req-method))
                    req-uri#
                    :query query-params#
                    :parameters (http/map->params {:use-expect-continue false})
                    :as :json
                    ))))))


;;Define Esri methods

;Get request token for user
(def-esri-method generate-token
  :arcgis-online
  :post
  "/sharing/rest/generatetoken"
  [:username :password :client]
  [:referer :ip :expiration]
  (comp :content raw-handler))


;Get request token for server application
(def-esri-method get-app-request-token
  :arcgis-online
  :post
  "/sharing/rest/oauth2/token"
  [:client_id :client_secret :grant_type]
  []
  (comp :content raw-handler))


(def-esri-method is-service-name-available
  :arcgis-online
  :post
  "/sharing/rest/portals/::user::/isServiceNameAvailable"
  [:user :name :type]
  []
  (comp :content raw-handler))



(def-esri-method get-services
  :arcgis-server
  :get
  "/arcgis/rest/services"
  []
  []
  (comp :content raw-handler))


;Gets information about a FeatureService.
;See: http://resources.arcgis.com/en/help/arcgis-rest-api/index.html#/Feature_Service/02r3000000z2000000/
(def-esri-method get-feature-service-info
  :arcgis-server
  :get
  "/arcgis/rest/services/::name::/FeatureServer"
  [:name]
  []
  (comp :content raw-handler))


(def-esri-method create-feature-service
  :arcgis-online
  :post
  "/sharing/rest/content/users/::user::/createService"
  [:user :targettype :createparameters]
  []
  (comp :content raw-handler))


;Adds new features to a FeatureService
;See: http://resources.arcgis.com/en/help/arcgis-rest-api/index.html#/Add_Features/02r30000010m000000/
(def-esri-method add-features
  :arcgis-server
  :post
  "/arcgis/rest/services/::feature_service_name::/FeatureServer/addFeatures"
  [:feature_service_name :features]
  []
  (comp :content raw-handler))
