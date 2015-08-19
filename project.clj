(defproject com.cemerick/friend "0.2.2-SNAPSHOT"
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
                 [commons-codec/commons-codec "1.9"]

                 ;; openid
                 [org.clojure/core.cache "0.6.2"]
                 [org.openid4java/openid4java-nodeps "0.9.6"
                  ; the openid4java artifact refers to a now-disappeared guice repo that
                  ; was previously hosted via google code svn :X
                  :exclusions [com.google.code.guice/guice]]
                 [com.google.inject/guice "2.0"]
                 [net.sourceforge.nekohtml/nekohtml "1.9.10"]
                 ; need different httpclient rev for https://issues.apache.org/jira/browse/HTTPCLIENT-1118
                 [org.apache.httpcomponents/httpclient "4.3.5"]

                 ]
  :plugins [[lein-ring "0.8.12"]]
  :ring {:handler test-friend.mock-app/mock-app :port 8080}
  :deploy-repositories {"releases" {:url "https://clojars.org/repo/" :creds :gpg}
                        "snapshots" {:url "https://clojars.org/repo/" :creds :gpg}}

  :profiles {:dev {:source-paths ["dev" "src" "test"]
                   :dependencies [[org.clojure/tools.namespace "0.2.7"]
                                  [org.clojure/java.classpath "0.2.2"]
                                  [ring-mock "0.1.1"]
                                  [aprint "0.1.3-SNAPSHOT"]
                                  [compojure "1.1.5"]
                                  [ring "1.2.0"]
                                  [robert/hooke "1.3.0"]
                                  [clj-http "1.0.0"]
                                  [expectations "2.0.9"]]}
             :sanity-check {:aot :all
                            :warn-on-reflection true
                            :compile-path "target/sanity-check-aot"}}
  :aliases  {"all" ["with-profile" "1.5:dev"]
             "sanity-check" ["do" "clean," "with-profile" "sanity-check" "compile"]})

;; see:
;; http://static.springsource.org/spring-security/site/docs/3.1.x/reference/springsecurity-single.html#overall-architecture


;; oauth
; https://github.com/DerGuteMoritz/clj-oauth2 OR
; https://github.com/mattrepl/clj-oauth

; https://github.com/fernandezpablo85/scribe-java
; http://technistas.com/2011/06/28/building-an-oauth-enabled-website-using-java/
