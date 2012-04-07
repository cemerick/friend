(ns cemerick.friend.util
  (:use [clojure.core.incubator :only (-?>>)]))

(defn gets
  "Returns the first value mapped to key found in the provided maps."
  [key & maps]
  (-?>> (map #(find % key) maps)
        (remove nil?)
        first
        val))