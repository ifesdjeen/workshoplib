(ns workshoplib.core
  (:import java.util.UUID)
  (:require [clojure.java.io :as io]
            [clojure.string :refer [join split]]
            [clojure.walk :refer [keywordize-keys]]
            [clj-http.client :as http]
            [cheshire.core :as json]))

(def endpoint "http://clojureworkshop.com:8080")

(declare token)

(defn path-for
  "Transform a namespace into a .clj file path relative to classpath root."
  [namespace]
  (str (if (re-matches #".*test" namespace)
         "test/" "src/")
       (-> (str namespace)
           (.replace \- \_)
           (.replace \. \/))
       ".clj"))

(defn- check-password-url
  []
  (format "%s/check_password" endpoint))

(defn- submit-report-url
  []
  (format "%s/reports" endpoint))

(defn try-password
  [password]
  (= 200
     (:status
      (http/post (check-password-url)
                 {:form-params      {:password password}
                  :throw-exceptions false}))))

(defn generate-token
  []
  (let [token (.toString (java.util.UUID/randomUUID))]
    (spit ".token" token)
    token))

(defn get-token
  []
  (if (.exists (io/file ".token"))
    (let [token (slurp ".token")]
      (if (empty? token)
        (generate-token)
        token))
    (generate-token)))

(defn send-test-report
  [report]
  (future
    (http/post (submit-report-url)
               {:form-params     (assoc report :token (get-token))
                :throw-exceptions false})))
