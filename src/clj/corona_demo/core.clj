(ns corona-demo.core
  (:gen-class)
  (:require
   [corona-demo.data :as data]
   [corona.client :as solr.client]
   [corona.schema :as solr.schema]))

;;; 1. Get Movies Dataset

;; We are going to be using a subset a The Movie Database

#_(first data/movies)
#_(count data/movies)





;;; 2. Setup Solr Core

;; For emacs user, see corona doc/Installation.md

(def core-dir      (str (System/getProperty "user.dir") "/resources/solr/tmdb"))
(def conf-dir      (str core-dir "/conf"))
(def data-dir      (str core-dir "/data"))
(def client-config {:type :http :core :tmdb})

(defn core-reset!
  []
  (println "SOLR: Deleting :tmdb core...")
  ;; same as $SOLR_HOME/bin/solr delete -c tmdb
  (solr.client/delete-core! client-config {:delete-index? true})

  (println "SOLR: Creating back :tmdb core...")
  ;; same as $SOLR_HOME/bin/solr create -c tmdb -d conf-dir
  (solr.client/create-core! client-config {:instance-dir core-dir})

  (println "SOLR: Ready to add and index documents :-)"))

#_(core-reset!)





;;; 3. Add Solr Schema Types

(def custom-schema data/schema-type-text_en_splitting)

(defn get-types-fields
  []
  (println "SOLR: Fetching actual field types")
  (solr.schema/get-field-types client-config))

(defn add-schema-types!
  []
  (println "SOLR: adding schema types")
  (solr.schema/add-field-type! client-config custom-schema))

;; if you just pulled this demo, all field types are already in manageschema
;; thus, and no action is required.

#_(add-schema-types!)



;;; 4. Add Solr Schema Fields

(defn get-fields []
  (println "SOLR: Fetching actual fields")
  (solr.schema/get-fields client-config))

(defn add-schema-fields!
  "Writes to resources/solr/tmdb/conf/manageschema adding new <field> lines."
  [new-schema-fields]
  (println "SOLR: adding schema fields")
  (solr.schema/add-field! client-config new-schema-fields))

(defn replace-schema-fields!
  "Writes to resources/solr/tmdb/conf/manageschema replacing existing <field> lines."
  [schema-fields]
  (println "SOLR: replacing schema fields")
  (solr.schema/replace-field! client-config schema-fields))

(def schema-fields (concat data/basic-fields data/content-fields data/number-fields))

;; if you just pulled this demo, all fields are already in manageschema
;; thus, and no action is required.
#_(add-schema-fields! [{}])
#_(replace-schema-fields! schema-fields)



;;; 5. Index documents (movies)

(defn core-index-all!
  []
  (println "SOLR: Clearing all index")
  (println (solr.client/clear-index! client-config {:commit true}))
  (println "SOLR: Indexing all documents")
  (println (solr.client/add! client-config data/movies {:commit true}))
  (println "SOLR: Documents uploaded and available at"
           "http://localhost:8983/solr/tmdb/query?q=*:*&fl=db_id,title,overview,genres,cast")
  )

#_(core-index-all!)




(defn -main
  []
  (println "running collage-demo.core/-main"))
