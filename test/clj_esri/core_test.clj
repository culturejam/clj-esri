(ns clj-esri.core-test
  (:require [clojure.test :refer :all]
            [clj-esri.core :refer :all]))


(deftest build-service-endpoint-defaults-test
  (testing "should build arcgis-online endpoint root"
    (is (= "http://www.arcgis.com"
           (build-service-endpoint :arcgis-online))))
  (testing "should build arcgis-server endpiont root"
    (is (= "http://sampleserver1.arcgisonline.com"
           (build-service-endpoint :arcgis-server)))))


(deftest build-service-endpoint-custom-test
  (with-arcgis-config "mytest.arcgis.com"
                      "mytest.arcgisonline.com"
    (testing "should build arcgis-online endpoint root with custom url"
      (is (= "http://mytest.arcgis.com"
             (build-service-endpoint :arcgis-online))))
    (testing "should build arcgis-server endpiont root with custom url"
      (is (= "http://mytest.arcgisonline.com"
             (build-service-endpoint :arcgis-server))))))


(deftest with-https-test
  (with-https
    (testing "should build arcgis-online endpoint root with https protocol"
      (is (= "https://www.arcgis.com"
             (build-service-endpoint :arcgis-online))))
    (testing "should build arcgis-server endpiont root with https protocol"
      (is (= "https://sampleserver1.arcgisonline.com"
             (build-service-endpoint :arcgis-server))))))




;API endpoints
(deftest expand-uri-test
  (testing "should replace tokens with values provided in the map"
    (is (= "sometest.com/beep" (expand-uri "sometest.com/::blip::"
                                       {:blip "beep"}))))
  (testing "should replace multiple tokens with values provided in the map"
    (is (= "sometest.com/beep/bar" (expand-uri "sometest.com/::blip::/::foo::"
                                           {:blip "beep" :foo "bar"}))))
  (testing "should not replace tokens with no value in map"
    (is (= "sometest.com/::blip::" (expand-uri "sometest.com/::blip::" {})))))


;simulate concatenating the complete URI
(deftest build-complete-endpoint-test
  (testing "simulate building complete api endpoint"
    (is (= "http://www.arcgis.com/sharing/rest/my/fake/endpoint"
       (str (build-service-endpoint :arcgis-online)
            (expand-uri "/sharing/rest/my/::real::/endpoint" {:real "fake"}))))))
