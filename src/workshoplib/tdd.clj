(ns workshoplib.tdd
  (:require workshoplib.core
            clojure.test
            [clojure.string :refer [replace]]))

(.addShutdownHook (Runtime/getRuntime) (Thread. #(Thread/sleep 2000)))

(def ^:dynamic *test-ns*)
(def ^:dynamic *not-implemented*)
(def ^:dynamic *not-implemented-fn-names*)

(gen-class
 :name workshoplib.NotImplementedException
 :extends RuntimeException)

(compile 'workshoplib.tdd)

(defn inc-not-implemented
  ([]
     (when (bound? (var *not-implemented*))
       (swap! *not-implemented* inc)))
  ([name]
     (inc-not-implemented)
     (when (bound? (var *not-implemented-fn-names*))
       (swap! *not-implemented-fn-names* conj name))))

(def … #(do
          (throw (workshoplib.NotImplementedException.))))

(defn implemented?
  [name]
  (-> (str (replace (str *test-ns*) #"-test" "") "/" (replace name #"-test" ""))
      symbol
      resolve
      meta
      :not-implemented
      not))

(defmacro try-expr
  "Used by the 'is' macro to catch unexpected exceptions.
  You don't call this."
  {:added "1.1"}
  [msg form]
  `(try ~(clojure.test/assert-expr msg form)
        (catch workshoplib.NotImplementedException e#)
        (catch Throwable t#
          (clojure.test/do-report {:type :error, :message ~msg,
                                   :expected '~form, :actual t#}))))

(defmacro is
  ([form] `(is ~form nil))
  ([form msg] `(try-expr ~msg ~form)))

(defmacro deftest
  [n & body]
  (when clojure.test/*load-tests*
    `(def ~(vary-meta n assoc :test `(fn []
                                       (let [n# ~(name n)
                                             impl# (implemented? n#)]
                                         (when (not impl#)
                                           (inc-not-implemented (str (str *test-ns*) "/" n#))
                                           (println n# " is not yet implemented, skipping."))
                                         (when impl#
                                           ~@body))))
       (fn []
         (clojure.test/test-var (var ~n))))))

(in-ns 'clojure.test)

(defn run-tests
  {:added "1.1"}
  ([] (run-tests *ns*))
  ([& namespaces]
     (binding [workshoplib.tdd/*not-implemented*          (atom 0)
               workshoplib.tdd/*not-implemented-fn-names* (atom [])]
       (let [summary (assoc (apply merge-with + (map (fn [ns]
                                                       (binding [workshoplib.tdd/*test-ns* ns]
                                                         (test-ns ns))) namespaces))
                       :type :summary)]
         (do-report summary)
         (workshoplib.core/send-test-report (assoc summary
                                              :ns (str *ns*)
                                              :not-implemented @workshoplib.tdd/*not-implemented*
                                              :not-implemented-fn-names @workshoplib.tdd/*not-implemented-fn-names*))
         summary))))
