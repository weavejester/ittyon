(defproject ittyon "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [medley "0.3.0"]
                 [intentions "0.1.1"]]
  :profiles
  {:dev {:jvm-opts ^:replace {}
         :source-paths ["dev"]
         :dependencies [[org.clojure/tools.namespace "0.2.4"]
                        [criterium "0.4.3"]
                        [jarohen/chord "0.4.1"]]}})
