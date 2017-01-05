(defproject com.cemerick/friend "0.3.0-SNAPSHOT"
  :description "Authentication and authorization library for Ring Clojure web apps and services."
  :url "http://github.com/cemerick/friend"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [ring/ring-core "1.2.0"]
                 [slingshot "0.10.2"]

                 [org.mindrot/jbcrypt "0.3m"]

                 ;; http-basic
                 [commons-codec "1.6"]]
  
  :deploy-repositories {"releases" {:url "https://clojars.org/repo/" :creds :gpg}
                        "snapshots" {:url "https://clojars.org/repo/" :creds :gpg}}
  
  :profiles {:dev {:dependencies [[ring-mock "0.1.1"]
                                  [compojure "1.1.5"]
                                  [ring "1.2.0"]
                                  [robert/hooke "1.3.0"]
                                  [clj-http "0.3.6"]]}
             :sanity-check {:aot :all
                            :warn-on-reflection true
                            :compile-path "target/sanity-check-aot"}
             :1.5 [:dev {:dependencies [[org.clojure/clojure "1.5.1"]]}]}
  :aliases  {"all" ["with-profile" "1.5:dev"]
             "sanity-check" ["do" "clean," "with-profile" "sanity-check" "compile"]})

;; see:
;; http://static.springsource.org/spring-security/site/docs/3.1.x/reference/springsecurity-single.html#overall-architecture


;; oauth
; https://github.com/DerGuteMoritz/clj-oauth2 OR
; https://github.com/mattrepl/clj-oauth

; https://github.com/fernandezpablo85/scribe-java
; http://technistas.com/2011/06/28/building-an-oauth-enabled-website-using-java/
