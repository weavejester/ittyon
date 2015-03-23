(defproject ittyon "0.3.2"
  :description "Manage distributed state for games"
  :url "https://github.com/weavejester/ittyon"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [medley "0.5.5"]
                 [intentions "0.1.3"]
                 [cljs-uuid "0.0.4"]]
  :plugins [[lein-cljsbuild "1.0.5"]
            [codox "0.8.11"]]
  :codox {:defaults {:doc/format :markdown}}
  :cljx
  {:builds
   [{:source-paths ["src"],  :output-path "target/generated/src",  :rules :clj}
    {:source-paths ["test"], :output-path "target/generated/test", :rules :clj}
    {:source-paths ["src"],  :output-path "target/generated/src",  :rules :cljs}
    {:source-paths ["test"], :output-path "target/generated/test", :rules :cljs}]}
  :prep-tasks   [["cljx" "once"]]
  :source-paths ["src" "target/generated/src"]
  :test-paths   ["test" "target/generated/test"]
  :cljsbuild
  {:builds
   [{:source-paths ["src" "test" "target/generated/src" "target/generated/test"]
     :compiler {:output-to "target/main.js"
                :pretty-print true}}]
   :test-commands {"unit-tests" ["phantomjs" :runner "target/main.js"]}}
  :aliases
  {"test-cljs" ["do" ["cljx" "once"] ["cljsbuild" "test"]]
   "test-all"  ["do" ["test"] ["cljsbuild" "test"]]}
  :profiles
  {:provided {:dependencies [[org.clojure/clojurescript "0.0-2850"]]}
   :dev      {:dependencies [[org.clojure/tools.namespace "0.2.9"]
                             [criterium "0.4.3"]
                             [jarohen/chord "0.6.0"]]
              :jvm-opts ^:replace {}
              :plugins [[com.keminglabs/cljx "0.6.0"]
                        [com.cemerick/clojurescript.test "0.3.3"]]}})
