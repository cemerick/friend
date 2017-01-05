(ns cemerick.friend.util
  (:require [ring.util.request :as req]))

(defn gets
  "Returns the first value mapped to key found in the provided maps."
  [key & maps]
  (some->> (map #(find % key) maps)
        (remove nil?)
        first
        val))

; this was never _really_ part of the API, was implemented (badly) before req/request-url was available
(def ^:deprecated original-url req/request-url)

(defn resolve-absolute-uri
  [^String uri request]
  (-> (original-url request)
    java.net.URI.
    (.resolve uri)
    str))
