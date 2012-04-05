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
                 [commons-codec "1.6"]
                 
                 ;; openid
                 [org.openid4java/openid4java-consumer "0.9.6" :type "pom"]
                 ; need different httpclient rev for https://issues.apache.org/jira/browse/HTTPCLIENT-1118
                 [org.apache.httpcomponents/httpclient "4.2-beta1"]]
  :profiles {:dev {:dependencies [[ring-mock "0.1.1"]
                                  [compojure "1.0.1"]
                                  [ring "1.0.2"]
                                  [clj-http "0.3.6-SNAPSHOT"]]}
             :1.2 {:dependencies [[org.clojure/clojure "1.2.0"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0-beta5"]]}}
  
  
  :dev-dependencies [[ring-mock "0.1.1"]
                     [compojure "1.0.1"]
                     [ring "1.0.2"]
                     [clj-http "0.3.6-SNAPSHOT"]])

;; see:
;; https://github.com/hassox/warden/wiki
;; http://static.springsource.org/spring-security/site/docs/3.1.x/reference/springsecurity-single.html#overall-architecture
;; https://github.com/bnoguchi/everyauth
;; https://github.com/intridea/omniauth
;; https://github.com/brentonashworth/sandbar

;; oauth
; https://github.com/DerGuteMoritz/clj-oauth2 OR
; https://github.com/mattrepl/clj-oauth
