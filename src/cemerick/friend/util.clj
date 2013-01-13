(ns cemerick.friend.util
  (:use [clojure.core.incubator :only (-?>>)]))

(defn gets
  "Returns the first value mapped to key found in the provided maps."
  [key & maps]
  (-?>> (map #(find % key) maps)
        (remove nil?)
        first
        val))

(defn original-url
  [{:keys [scheme server-name server-port uri query-string]}]
  (str (name scheme) "://" server-name
       (cond
         (and (= :http scheme) (not= server-port 80)) (str \: server-port)
         (and (= :https scheme) (not= server-port 443)) (str \: server-port)
         :else nil)
       uri
       (when (seq query-string)
         (str \? query-string))))

(defn resolve-absolute-uri
  [^String uri request]
  (-> (original-url request)
    java.net.URI.
    (.resolve uri)
    str))