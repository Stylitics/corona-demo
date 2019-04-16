(ns corona-demo.core
  (:gen-class)
  (:require
   [corona-demo.data      :as data]
   [corona-demo.ml.core   :as ml]
   [corona-demo.ml.cortex :as cortex]
   [corona-demo.ml.mxnet  :as mxnet]
   [corona-demo.utils     :as utils]
   [corona.core-admin     :as solr.core]
   [corona.index          :as solr.index]
   [corona.ltr            :as solr.ltr]
   [corona.query          :as solr.query]
   [corona.schema         :as solr.schema]))





;;;; CORONA DEMO WALKTHROUGH





;;; 1. Get Movies Dataset

;; We are going to be using a subset a The Movie Database

#_(first data/movies)
#_(count data/movies)





;;; 2. Setup Solr Core

;; For emacs user, see corona doc/Installation.md

(def core-dir      (str (System/getProperty "user.dir") "/resources/solr/tmdb"))
(def data-dir      (str core-dir "/data/"))
(def client-config {:type :http :core :tmdb})

(defn core-reset!
  []
  (println "SOLR: Deleting :tmdb core...")
  ;; same as $SOLR_HOME/bin/solr delete -c tmdb
  (solr.core/delete! client-config {:delete-index? true})

  (println "SOLR: Creating back :tmdb core...")
  ;; same as $SOLR_HOME/bin/solr create -c tmdb -d conf-dir
  (solr.core/create! client-config {:instance-dir core-dir})

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
  (println (solr.index/clear! client-config {:commit true}))
  (println "SOLR: Indexing all documents")
  (println (solr.index/add! client-config data/movies {:commit true}))
  (println "SOLR: Documents uploaded and available at"
           "http://localhost:8983/solr/tmdb/query?q=*:*&fl=db_id,title,overview,genres,cast")
  )

#_(core-index-all!)





;;; 6. Run a search and find a movie

;; return helpers
(defn return-docs [resp] (-> resp :response :docs))
(defn get-fields [fields docs] (mapv (apply juxt fields) docs))
(defn return-fields [fields resp] (->> resp return-docs (get-fields fields)))

(def default-query-settings
  {:defType "edismax"
   ;; main query
   :q "*:*"
   ;; Boosts: Content fields
   :qf [["genres" 10] ["overview" 6] ["title" 5] ["tagline" 1] ["keywords" 1]]
   ;; Boosts: Number fields
   :bf ["recip(ms(NOW/HOUR,release_date),3.16e-11,0.08,0.05)^50"
        "if(gt(popularity,50),1,0)^10"]
   ;; Response: fields to be returned
   :fl ["db_id" "genres" "keywords" "title" "overview" "release_date" "budget" "score"]
   ;; Response: number of results returned
   :rows 30
   ;; Rerank
   ;; :rq "{!ltr model=ltrGoaModel reRankDocs=100 efi.gender=1 efi.age=20 efi.occupation=1}"
   })

(defn query-simple
  [settings]
  (solr.query/query
   client-config
   (merge default-query-settings settings)))

;; Search for a Bond Movie
#_(return-fields
   [:db_id :title :score]
   (query-simple {:q "Bond"}))





;;; 6.1 Run a basic MoreLikeThis query from chosen movie

(def bond-spectre-movie {:db_id "206647"
                         :title "Spectre"
                         :release_date #inst "2015-10-26T00:00:00.000-00:00"})

(def bond-never-movie {:db_id "36670"
                       :title "Never Say Never Again"
                       :release_date #inst "1983-01-01T00:00:00.000-00:00"})

(def bourne-movie {:db_id "324668"
                   :title "Jason Bourne"
                   :release_date #inst "2016-07-27T00:00:00.000-00:00",
                   :genres ["Action" "Thriller"],
                   :overview "The most dangerous former operative of the CIA is drawn out of hiding to uncover hidden truths about his past.",
                   :keywords ["assassin" "amnesia" "flashback"]})

(def default-mlt-settings
  {;; main query to select template document:
   :q "db_id:*"
   ;; Where to extract interesting terms for similarity?
   :mlt.fl (mapv :name data/content-fields)
   ;; Minimum Term Frequency below which terms will be ignored
   :mlt.mintf "1"
   ;; Minimum Document Frequency below which terms will be ignored
   :mlt.mindf "3"
   ;; Should we boost extracted interesting terms base on their tf-idf score?
   :mlt.boost "true"
   ;; Boost interesting terms per field:
   :mlt.qf [["genres" 10] ["overview" 6] ["title" 3] ["tagline" 1] ["keywords" 1]]
   ;; Response: assoc interesting-terms used
   :mlt.interestingTerms "details"
   ;; Response: fields to retrieve
   :fl ["db_id" "title" "overview" "release_date" "budget" "keywords" "score"]
   ;; !rerank\!ltr query. in this case - rerank that uses external rqq query for similar docs reranking
   ;; :rq "{!ltr model=ltrGoaModel reRankDocs=100 efi.gender=1 efi.age=20 efi.occupation=1}"
   })


(defn query-mlt-simple
  [settings]
  (solr.query/query-mlt
   client-config
   (merge default-mlt-settings settings)))

;; More Like Spectre Bond Movie:

#_(return-fields [:title :score :release_date]
   (query-mlt-simple {:q (str "db_id:" (:db_id bond-spectre-movie))}))

;; How does it work? It builds query from interestingTerms.
;; Let's find out what they are: ([<field> <value> <score>])
#_(solr.query/mlt-resp->terms
   (query-mlt-simple {:q (str "db_id:" (:db_id bond-spectre-movie))}))

;; Can we boost by non-content fields like release_date?

#_(return-fields
   [:title :score :release_date]
   (query-mlt-simple {:q (str "db_id:" (:db_id bond-spectre-movie))
                      :now (inst-ms (:release_date bond-spectre-movie))
                      :bf ["recip(sub(${now},ms(release_date)),3.16e-11,1,1)^30"]}))

;; hmm no, we get the exact same results.






;;; 6.2 Run a custom MLT handler that accepts boosting fns

;; Let's use edismax normal query, but passing interesting terms found for Spectre. 
;; For this We have a special handler called query-mlt-tv-edismax
;; tv stands for termVectors.

(defn query-mlt-custom
  [settings]
  (solr.query/query-mlt-tv-edismax
   client-config
   (merge default-mlt-settings settings)))

;; We need to make sure our manageschema file have termVectors set to true for content fields
;; then use special key :mlt.q as :q is also usable for normal edismax search query
;; by default this is the case:
#_(return-fields
   [:title :score :release_date]
   (query-mlt-custom
    {:mlt.q (str "db_id:" (:db_id bond-spectre-movie))
     :mlt.qf [["genres" 5] ["overview" 6] ["title" 3] ["tagline" 1] ["keywords" 1]]
     :now (inst-ms (:release_date bond-spectre-movie))
     :bf ["recip(sub(${now},ms(release_date)),3.16e-11,1,1)^2"]}))

#_(solr.query/mlt-resp->terms
   (query-mlt-simple {:q (str "db_id:" (:db_id bond-spectre-movie))}))

;; That's good, it picks up most recent Bonds as well as other similar movie.

;; If we try with old bond:

#_(return-fields
 [:title :score :release_date]
 (query-mlt-custom
  {:mlt.q (str "db_id:" (:db_id bond-never-movie))
   :mlt.qf [["genres" 5] ["overview" 6] ["title" 3] ["tagline" 1] ["keywords" 1]]
   :now (inst-ms (:release_date bond-never-movie))
   :bf ["recip(sub(${now},ms(release_date)),3.16e-11,1,1)^2.5"]}))

;; Older Bonds come up.

;; But say you are new on the site and we don't have your movie history, etc. Can we guess what you would like?






;;; 6.3 Run query but re-rank top 100 results using collaborative filtering.

;; That is right, we have MovieLens user ratings open source datasets.
;; Let's relying on those.
;; First let's try with already built ML model available at:
;; resources/solr/tmdb/conf/_schema_model-store.json

#_(return-fields
   [:title :score :release_date]
   (query-mlt-custom
    {:mlt.q (str "db_id:" (:db_id bond-spectre-movie))
     :now (inst-ms (:release_date bond-spectre-movie))
     :bf ["recip(sub(${now},ms(release_date)),3.16e-11,1,1)^2"]
     :rq "{!ltr model=ltrGoaModel reRankDocs=100 efi.gender=1 efi.age=20 efi.occupation=1}"}))


;;; Now let's try with other user profile.

#_(return-fields
 [:title :score :release_date]
 (query-mlt-custom
  {:mlt.q (str "db_id:" (:db_id bond-spectre-movie))
   :now (inst-ms (:release_date bond-spectre-movie))
   :bf ["recip(sub(${now},ms(release_date)),3.16e-11,1,1)^2"]
   :rq "{!ltr model=ltrGoaModel reRankDocs=100 efi.gender=0 efi.age=60 efi.occupation=7}"}))

;; Completely different results

;; NOTE: we could be still taking into account actual first rank to get
;; an hybrid solution that is weighting content results too.

;; Now, let's build the model!






;;; 7. Upload features


(def movie-features (ml/build-features "tmdb_features" data/movies))

(comment

  (first movie-features)

  (solr.ltr/upload-features! client-config movie-features)
  ;; Check uploaded features at
  ;; http://localhost:8983/solr/tmdb/schema/feature-store/tmdb_features

  ;; And feature extraction at
  ;; http://localhost:8983/solr/tmdb/query?q=*:*&fl=db_id,genres,production_countries,spoken_languages,[features%20store=tmdb_features]&rows=500
)






;;; 8. Prepare training and testing datasets

(comment

  ;; Extract features

  (count (ml/extract-mov-features {:type :http :core :tmdb} "tmdb_features"))


  ;; Build dataset

  (def ds (ml/build-training-dataset))

  (first ds)


  ;; Check ds balance (incanter)

  ;; (icore/view (icharts/histogram (mapv (comp first :score) ds) :nbins 100))

  ;; dataset is unbalanced
  ;; we do need normalize it since we have:
  ;; - huge values for budget revenue etc
  ;; - unbalanced dataset


  ;; Normalize dataset

  (def normed (ml/normalize-training-dataset ds))

  (def normed-ds (:ds normed))

  (count normed-ds) ; ~ 10k

  ;; to see what changed

  ;; (icore/view (icharts/histogram (mapv (comp first :score) normed-ds) :nbins 100))


  ;; Shuffle dataset

  (def shuffled-ds (shuffle normed-ds)) ; with starting MSE ~2


  ;; Prepare train\test sets

  (defn upload-train-and-test-datasets!
    [dataset]
    (let [total-vol (count shuffled-ds)
          ratio 0.8
          train-vol (long (* 1000 (quot (* ratio total-vol) 1000)))
          test-vol (long (* 1000 (quot (* (- 1. ratio) total-vol) 1000)))]
      (spit (str data-dir "train.txt") (vec (take train-vol shuffled-ds)))
      (spit (str data-dir "test.txt")  (vec (take test-vol (drop train-vol shuffled-ds))))))

  (upload-train-and-test-datasets! shuffled-ds)

  )






;;; 9. Run Neural Networks training

(comment

  ;;; A. Either load already trained NN from file:
  (def models-dir (str data-dir "/models/"))
  (def trained-net (cortex/load-trained-net (str models-dir "ltr-goa-nn.nippy")))
  ;; mxnet here

  ;;; B. or train your own NN
  ;; Load datasets

  (def train-ds (utils/read-file (str data-dir "train.txt")))
  (def test-ds  (utils/read-file (str data-dir "test.txt")))

  (first train-ds)
  (first test-ds)

  (cortex/train! train-ds test-ds 1000 (str models-dir "ltr-goa-nn-new"))

  ;; FIXME: mxnet version
  (mxnet/train 1000)
  (mxnet/render-model (str models-dir "ltr-goa-nn-mxnet-new"))


  ;; Then load newly trained model:

  (def trained-net (cortex/load-trained-net (str models-dir "ltr-goa-nn-new.nippy")))
  ;; FIXME: load mxnet model


  ;; to see what we've got predict scoring for top 30 entries of test set (excluding entries with mid ranges)
  (->> (-> splitted-ds :test)
       (remove (fn [{:keys [score]}]
                 (and (> (first score) 2.1) (< (first score) 5.0))))
         (take 30)
         (map (fn [entry]
                {:act-score (-> entry :score first)
                 :net-score (-> (cortex/run-trained-net trained-net [entry])
                                first
                                :score
                                first)})))

  )






;;; 10. Build Neural Networks Model

(comment
  ;; Finally we can upload model
  (def tmdb-model (ml/build-nn-model trained-net normed movie-features))

  (solr.ltr/upload-model! client-config tmdb-model)

  ;; to see models go to
  ;; http://localhost:8983/solr/tmdb/schema/model-store
)






(defn -main
  []
  (println "running corona-demo.core/-main"))
