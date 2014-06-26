(defproject ittyon "0.0.5"
  :description "Manage distributed state for games"
  :url "https://github.com/weavejester/ittyon"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [medley "0.4.0"]
                 [intentions "0.1.1"]
                 [cljs-uuid "0.0.4"]]
  :plugins [[lein-cljsbuild "1.0.3"]
            [com.keminglabs/cljx "0.4.0"]
            [com.cemerick/clojurescript.test "0.3.1"]]
  :cljx
  {:builds
   [{:source-paths ["src"],  :output-path "target/generated/src",  :rules :clj}
    {:source-paths ["test"], :output-path "target/generated/test", :rules :clj}
    {:source-paths ["src"],  :output-path "target/generated/src",  :rules :cljs}
    {:source-paths ["test"], :output-path "target/generated/test", :rules :cljs}]}
  :source-paths ["src" "target/generated/src"]
  :test-paths   ["test" "target/generated/test"]
  :hooks [cljx.hooks]
  :cljsbuild
  {:builds
   [{:source-paths ["src" "test" "target/generated/src" "target/generated/test"]
     :compiler {:output-to "target/main.js"
                :pretty-print true}}]
   :test-commands {"unit-tests" ["phantomjs" :runner "target/main.js"]}}
  :aliases
  {"test-cljs" ["do" ["cljx" "once"] ["cljsbuild" "test"]]
   "test-all"  ["do" ["with-profile" "default:+1.6" "test"] ["cljsbuild" "test"]]}
  :profiles
  {:provided {:dependencies [[org.clojure/clojurescript "0.0-2227"]]}
   :1.6      {:dependencies [[org.clojure/clojure "1.6.0"]]}
   :dev      {:jvm-opts ^:replace {}
              :dependencies [[org.clojure/tools.namespace "0.2.4"]
                             [criterium "0.4.3"]
                             [jarohen/chord "0.4.1"]]}})
