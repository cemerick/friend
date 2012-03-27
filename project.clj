(defproject com.cemerick/friend "0.0.1-SNAPSHOT"
  :description "Authentication and authorization library for Ring Clojure web apps and services."
  :url "http://github.com/cemerick/friend"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [ring/ring-core "1.0.2"]
                 [slingshot "0.10.2"]
                 [robert/hooke "1.1.2"]
                 
                 [org.mindrot/jbcrypt "0.3m"]
                 
                 ;; http-basic
                 [org.clojure/data.codec "0.1.0"]]
  :dev-dependencies [[ring-mock "0.1.1"]]
  :profiles {:dev {:dependencies [[ring-mock "0.1.1"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0-alpha1"]]}})

;; see:
;; https://github.com/hassox/warden/wiki
;; http://static.springsource.org/spring-security/site/docs/3.1.x/reference/springsecurity-single.html#overall-architecture
;; https://github.com/bnoguchi/everyauth
;; https://github.com/intridea/omniauth
;; https://github.com/brentonashworth/sandbar

