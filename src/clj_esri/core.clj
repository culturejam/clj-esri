(ns clj-esri.core
  (:use [clojure.data.json :only [read-json]])
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [com.twinql.clojure.http :as http]
            [oauth.client :as oauth]
            [oauth.signature]))


(declare status-handler)

(def ^:dynamic *protocol* "http")
(def ^:dynamic *access-token* nil)
(def ^:dynamic *arcgis-online-endpoint* "www.arcgis.com/sharing/rest")
(def ^:dynamic *arcgis-server-endpoint* "sampleserver1.arcgisonline.com/arcgis/rest/services")


;; Get JSON from clj-apache-http
(defmethod http/entity-as :json [entity as state]
  (read-json (http/entity-as entity :string state)))


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
             query-params#(merge required-hash-map# optional-hash-map#)
             req-uri# (str (build-service-endpoint ~service-type) (expand-uri ~req-url required-hash-map#))
             ]
         (~handler (~(symbol "http" (name req-method))
                    req-uri#
                    :query (merge query-params#
                                  {:f "json"}
                                  (if *access-token*
                                    {:token *access-token*}))
                    :parameters (http/map->params {:use-expect-continue false})
                    :as :json
                    ))))))
