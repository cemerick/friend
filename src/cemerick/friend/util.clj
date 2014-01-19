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
  [{:keys [scheme server-name server-port uri query-string headers]}]
  ;; If your proxy doesn't send x-forwarded-proto headers, then you'll need to
  ;; set the return URL explicitly on the request going into the openid
  ;; middleware...
  (let [forwarded-port (get headers "x-forwarded-port")
        forwarded-proto (get headers "x-forwarded-proto")
        scheme (name (or forwarded-proto scheme))
        port (cond
               forwarded-port (str \: forwarded-port)
               forwarded-proto nil
               (and (= "http" scheme) (not= server-port 80)) (str \: server-port)
               (and (= "https" scheme) (not= server-port 443)) (str \: server-port)
               :else nil)]
    (str scheme "://" server-name
         port
         uri
         (when (seq query-string)
           (str \? query-string)))))

(defn resolve-absolute-uri
  [^String uri request]
  (-> (original-url request)
    java.net.URI.
    (.resolve uri)
    str))
