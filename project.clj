(defproject collage-demo "0.1.0-SNAPSHOT"
  :description "A search and recommendation engine demo app that uses Clojure Solr 7 ML and Movies"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/data.csv "0.1.4"]
                 [org.clojure/data.json "0.2.6"]

                 ;; SOLR: 7.6.0:
                 [corona "0.1.0-SNAPSHOT"]

                 ;; CORTEX
                 [thinktopic/cortex "0.9.22"]
                 [thinktopic/experiment "0.9.22"]

                 ;; MXNET: Choose your MxNet dep:
                 #_[org.apache.mxnet.contrib.clojure/clojure-mxnet-linux-cpu "1.4.0"]
                 [org.apache.mxnet.contrib.clojure/clojure-mxnet-osx-cpu "1.4.0"]
                 ]

  :resource-paths ["resources" "target"]
  :source-paths ["src/clj" "src/cljc"]
  :clean-targets ^{:protect false} ["target"]
  :main corona-demo.core)
