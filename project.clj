(defproject dragonspawn "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1844"]
                 [domina "1.0.0"]]
  :plugins [[lein-cljsbuild "0.3.2"]]
  :source-paths ["src/clj"]
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src/cljs"]
                        :compiler {:output-dir "resources/public"
                                   :output-to "resources/public/game.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}
                       {:id "prod"
                        :source-paths ["src/cljs"]
                        :compiler {:output-to "resources/public/game.adv.js"
                                   :pretty-print false
                                   :optimizations :advanced}}]})
