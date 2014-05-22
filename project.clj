(defproject ittyon "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [medley "0.2.0"]]
  :profiles
  {:dev {:jvm-opts ^:replace {}
         :dependencies [[criterium "0.4.3"]]}})
