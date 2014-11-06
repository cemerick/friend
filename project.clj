(defproject com.cemerick/friend "0.2.3-SNAPSHOT"
  :description "Authentication and authorization library for Ring Clojure web apps and services."
  :url "http://github.com/cemerick/friend"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [ring/ring-core "1.3.1"]
                 [slingshot "0.12.1"]

                 [org.mindrot/jbcrypt "0.3m"]

                 ;; http-basic
                 [commons-codec "1.9"]

                 ;; openid
                 [org.clojure/core.cache "0.6.4"]
                 [org.openid4java/openid4java-nodeps "0.9.6"
                  ; the openid4java artifact refers to a now-disappeared guice repo that
                  ; was previously hosted via google code svn :X
                  :exclusions [com.google.code.guice/guice]]
                 [com.google.inject/guice "2.0"]
                 [net.sourceforge.nekohtml/nekohtml "1.9.10"]
                 ; need different httpclient rev for https://issues.apache.org/jira/browse/HTTPCLIENT-1118
                 [org.apache.httpcomponents/httpclient "4.3.5"]]
  
  :deploy-repositories {"releases" {:url "https://clojars.org/repo/" :creds :gpg}
                        "snapshots" {:url "https://clojars.org/repo/" :creds :gpg}}
  
  :profiles {:dev {:dependencies [[ring-mock "0.1.5"]
                                  [compojure "1.2.1"]
                                  [ring "1.3.1"]
                                  [robert/hooke "1.3.0"]
                                  [clj-http "1.0.1"]]}
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
