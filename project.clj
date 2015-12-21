(defproject ittyon "0.10.2"
  :description "Manage distributed state for games"
  :url "https://github.com/weavejester/ittyon"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.2.374"]
                 [medley "0.7.0"]
                 [intentions "0.2.0"]]
  :plugins [[com.cemerick/clojurescript.test "0.3.3"]
            [lein-cljsbuild "1.1.2"]
            [codox "0.8.13"]]
  :codox {:defaults {:doc/format :markdown}
          :src-dir-uri "http://github.com/weavejester/ittyon/blob/0.10.2/"
          :src-linenum-anchor-prefix "L"}
  :cljsbuild
  {:builds
   [{:source-paths ["src" "test"]
     :compiler {:output-to "target/main.js"
                :optimizations :whitespace}}]
   :test-commands {"unit-tests" ["phantomjs" :runner "target/main.js"]}}
  :aliases
  {"test"      ["test" "ittyon.core-test" "ittyon.client-server-test"]
   "test-cljs" ["cljsbuild" "test"]
   "test-all"  ["do" ["test"] ["cljsbuild" "test"]]}
  :profiles
  {:provided {:dependencies [[org.clojure/clojurescript "1.7.189"]]}
   :dev      {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                             [criterium "0.4.3"]
                             [jarohen/chord "0.6.0"]]
              :jvm-opts ^:replace {}}})
