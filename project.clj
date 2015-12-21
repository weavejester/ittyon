(defproject ittyon "0.10.2"
  :description "Manage distributed state for games"
  :url "https://github.com/weavejester/ittyon"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.2.374"]
                 [medley "0.7.0"]
                 [intentions "0.2.0"]]
  :plugins [[lein-cljsbuild "1.1.2"]
            [lein-doo "0.1.6"]
            [codox "0.8.13"]]
  :codox {:defaults {:doc/format :markdown}
          :src-dir-uri "http://github.com/weavejester/ittyon/blob/0.10.2/"
          :src-linenum-anchor-prefix "L"}
  :cljsbuild
  {:builds
   [{:id "test"
     :source-paths ["src" "test"]
     :compiler {:output-to "target/test-runner.js"
                :output-dir "target"
                :optimizations :whitespace
                :main ittyon.test-runner}}]}
  :aliases
  {"test-cljs" ["doo" "phantom" "test" "once"]
   "test-all"  ["do" ["test"] ["test-cljs"]]}
  :profiles
  {:provided {:dependencies [[org.clojure/clojurescript "1.7.189"]]}
   :dev      {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                             [criterium "0.4.3"]
                             [jarohen/chord "0.6.0"]]
              :jvm-opts ^:replace {}}})
