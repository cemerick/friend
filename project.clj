(defproject org.bmillare/friend "0.1.0"
  :description "Authentication and authorization library for Ring Clojure web apps and services, decoupled, forked from cemerick/friend"
  :url "http://github.com/bmillare/friend"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[ring/ring-core "1.2.1"]
                 [slingshot "0.10.3"]
                 
                 ;; [org.mindrot/jbcrypt "0.3m"]

                 ;; http-basic
                 [commons-codec "1.6"]

                 ;; openid
                 #_ [org.clojure/core.cache "0.6.2"]
                 #_ [org.openid4java/openid4java-nodeps "0.9.6"
                  ; the openid4java artifact refers to a now-disappeared guice repo that
                  ; was previously hosted via google code svn :X
                 #_ #_  :exclusions [com.google.code.guice/guice]]
                 #_ [com.google.inject/guice "2.0"]
                 #_ [net.sourceforge.nekohtml/nekohtml "1.9.10"]
                 ; need different httpclient rev for https://issues.apache.org/jira/browse/HTTPCLIENT-1118
                 #_ [org.apache.httpcomponents/httpclient "4.2.1"]])

;; see:
;; http://static.springsource.org/spring-security/site/docs/3.1.x/reference/springsecurity-single.html#overall-architecture


;; oauth
; https://github.com/DerGuteMoritz/clj-oauth2 OR
; https://github.com/mattrepl/clj-oauth

; https://github.com/fernandezpablo85/scribe-java
; http://technistas.com/2011/06/28/building-an-oauth-enabled-website-using-java/
