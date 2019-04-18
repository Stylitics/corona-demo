(ns corona-demo.core
  (:gen-class)
  (:require
    [clojure.string :as string]
    [corona-demo.data :as data]
    [corona-demo.ml.core :as ml]
    [corona-demo.ml.cortex :as cortex]
    [corona-demo.ml.mxnet :as mxnet]
    [corona-demo.utils :as utils]
    [corona.core-admin :as solr.core]
    [corona.index :as solr.index]
    [corona.ltr :as solr.ltr]
    [corona.query :as solr.query]
    [corona.schema :as solr.schema]

    [incanter.core :as icore]
    [incanter.charts :as icharts]))





;;;; CORONA DEMO WALKTHROUGH





;;; 1. Get Movies Dataset

;; We are going to be using a subset a The Movie Database

#_(first data/movies)
#_(count data/movies)





;;; 2. Setup Solr Core

;; For emacs user, see corona doc/Installation.md

(def core-dir (str (System/getProperty "user.dir") "/resources/solr/tmdb"))
(def data-dir (str core-dir "/data/"))
(def client-config {:core :tmdb})

(defn core-reset!
  []
  (println "SOLR: Deleting :tmdb core...")
  ;; same as $SOLR_HOME/bin/solr delete -c tmdb
  (solr.core/delete! client-config {:deleteIndex true})

  (println "SOLR: Creating back :tmdb core...")
  ;; same as $SOLR_HOME/bin/solr create -c tmdb -d conf-dir
  (solr.core/create! client-config {:instanceDir core-dir})

  (println "SOLR: Ready to add and index documents :-)"))

#_(core-reset!)



#_(first data/movies)

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
#_(doseq [n (map :name data/content-fields)]
    (solr.schema/update-field!
      client-config
      {:add-copy-field {:source n :dest "_text_"}}))





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

;; Get me bond movies
#_(solr.query/query
    client-config
    {:q    "bond"
     :fl   ["db_id" "title" "score"]                        ; Results: Fields
     :rows 10})

;; with Daniel Craigh
#_(solr.query/query
    client-config
    {:q    "bond cast:Daniel Craig"
     :fl   ["db_id" "title" "score"]                        ; Results: Fields
     :rows 10})

;; from last 20 years
#_(solr.query/query
    client-config
    {:defType "lucene"
     :q       "bond"
     :fq      "release_date:[NOW-20YEARS TO NOW]"
     :fl      ["db_id" "title" "release_date"]              ; Results: Fields
     :rows    10})

;; prefer more recent
#_(solr.query/query
    client-config
    {:defType "edismax"
     :q       "(overview:bond)"
     :bf      ["recip(ms(NOW,release_date),3.16e-11,0.5,0.8)^10"]
     :fl      ["db_id" "title" "release_date"]              ; Results: Fields
     :rows    10})






;;; 6.1 Run a basic MoreLikeThis query from chosen movie

(def bond-spectre-movie {:db_id        "206647"
                         :title        "Spectre"
                         :release_date #inst "2015-10-26T00:00:00.000-00:00"})

(def bond-never-movie {:db_id        "36670"
                       :title        "Never Say Never Again"
                       :release_date #inst "1983-01-01T00:00:00.000-00:00"})

(def bourne-movie {:db_id        "324668"
                   :title        "Jason Bourne"
                   :release_date #inst "2016-07-27T00:00:00.000-00:00",
                   :genres       ["Action" "Thriller"],
                   :overview     "The most dangerous former operative of the CIA is drawn out of hiding to uncover hidden truths about his past.",
                   :keywords     ["assassin" "amnesia" "flashback"]})

(def default-mlt-settings
  {:q         "db_id:206647"                                ;“this” matched by id
   :mlt.fl    ["overview" "genres" "title" "keywords"       ;interesting-terms from
               "production_companies" "production_countries"
               "spoken_languages" "director"]
   :mlt.mintf "1"                                           ;min Term Frequency below which terms are ignored
   :mlt.mindf "3"                                           ;min Document Frequency below which terms are ignored
   :mlt.minwl "3"                                           ;min Word Length
   :mlt.boost "true"                                        ;interesting terms tf-idf score as boost
   :mlt.qf    [["genres" 10] ["overview" 6] ["title" 3] ["keywords" 1]] ;boost
   :fl        ["db_id" "title" "release_date" "score"]})


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
    (query-mlt-simple {:q                    (str "db_id:" (:db_id bond-spectre-movie))
                       :mlt.interestingTerms "details"}))

;; Can we boost by non-content fields like release_date?

#_(return-fields
    [:title :score :release_date]
    (query-mlt-simple {:q   (str "db_id:" (:db_id bond-spectre-movie))
                       :now (inst-ms (:release_date bond-spectre-movie))
                       :bf  ["recip(sub(${now},ms(release_date)),3.16e-11,1,1)^30"
                             "if(gt(popularity,50),1,0)^10"]}))

;; hmm no, we get the exact same results.





;;; 6.2 Run a custom MLT handler that accepts boosting fns

;; Let's use edismax normal query, but passing interesting terms found for Spectre. 
;; For this We have a special handler called query-mlt-tv-edismax
;; tv stands for termVectors.


(defonce ratings (data/read-ratings))

(defn prefered-recent-movies-query
  [user-id]
  (->> ratings
       (filter #(= (:userId %) user-id))
       (filter #(> (:rating %) 4.0))
       (map (fn [r] (update r :timestamp #(Integer. %))))
       (sort-by :timestamp >)
       (take 5)
       (mapv :movieId)))

#_(prefered-recent-movies-query 23)

(solr.query/query
  client-config
  {:q  "db_id:1222" #_(format "db_id:%s" (string/join " OR db_id:" (prefered-recent-movies-query 23)))
   :fl ["db_id" "title" "overview" "score"]})

(defn custom-interesting-terms
  [actual-movie-id user-id mlt-fl]
  (let [pref-ids (prefered-recent-movies-query user-id)
        pref-ids-str (string/join " db_id:" pref-ids)]
    (solr.query/query-term-vectors
      client-config
      {:q         (format "db_id:%s db_id:%s" actual-movie-id pref-ids-str)
       :mlt.minwl "3"
       :fl        mlt-fl})))

#_(solr.query/term-vectors-resp->interesting-terms-per-field
    (custom-interesting-terms
      (:db_id bond-spectre-movie)
      23
      ["genres" "overview" "keywords"])
    [["genres" 10]])

#_(solr.query/query client-config {:q "{!mlt qf=title^100 qf=author=^50}db_id:"})


(defn tv-terms->q
  [tv-terms & [q]]
  (let [tv-terms-str (solr.query/terms-per-field->q tv-terms)]
    (format "(%s) (%s)" tv-terms-str q)))

(defn query-mlt-tv-edismax
  "Like more like this handler query or `query-mlt` but

  - takes top-k terms *PER FIELD*, for more explanations, see
    https://github.com/DiceTechJobs/RelevancyFeedback#isnt-this-just-the-mlt-handler

  - allows edismax params (e.g. `:boost` `:bf` `:bq` `:qf`)
    NOTE: To better understand boosting methods, see
    https://nolanlawson.com/2012/06/02/comparing-boost-methods-in-solr/

  Special settings:

  :mlt.q <string>
  To reach the matching document to get interesting terms.

  Supported mlt keys: :mlt-fl, :mlt-qf

  IMPORTANT: All mlt.fl fields MUST be set as TermVectors=true in the managedschema
  for the mlt query to be integrated to main q.
  "
  [client-config {:keys [fq] :as settings}]
  (let [mlt-q (:mlt.q settings)
        tv-resp (solr.query/query-term-vectors
                  client-config
                  {:q  mlt-q
                   :fl (:mlt.fl settings)})
        tv-terms (solr.query/term-vectors-resp->interesting-terms-per-field
                   tv-resp
                   (:mlt.qf settings))
        new-q (tv-terms->q tv-terms (:q settings))
        new-fq (cond-> (format "-(%s)" mlt-q)
                       fq (str " " fq))
        settings (-> settings
                     (assoc :q new-q)
                     (assoc :fq new-fq)
                     (dissoc solr.query/mlt-keys)
                     (dissoc :mlt.q))
        resp (solr.query/query client-config (merge {:defType "edismax"}
                                                    settings))]
    (assoc resp :interestingTerms tv-terms :match (-> tv-resp :response))))

(defn query-mlt-custom
  [settings]
  (query-mlt-tv-edismax
    client-config
    (merge default-mlt-settings settings)))

#_(prefered-movies-query ratings 18)



;; We need to make sure our manageschema file have termVectors set to true for content fields
;; then use special key :mlt.q as :q is also usable for normal edismax search query
;; by default this is the case:
(return-fields
  [:title :score :release_date]
  (let [more-like-these-q (format "db_id:(%s^10 %s^10)"
                                  (:db_id bond-spectre-movie)
                                  (:db_id bourne-movie))]
    (query-mlt-custom
      {:mlt.q  more-like-these-q
       :mlt.qf [["genres" 5] ["overview" 6] ["title" 3] ["tagline" 1] ["keywords" 1]]
       :fl     ["title" "score" "release_date"]
       :now    (inst-ms (:release_date bond-spectre-movie))
       :bf     ["recip(sub(${now},ms(release_date)),3.16e-11,1,1)^2"
                "if(gt(popularity,50),1,0)^1"]})))

#_(solr.query/mlt-resp->terms
    (query-mlt-simple {:q (str "db_id:" (:db_id bond-spectre-movie))}))

;; That's good, it picks up most recent Bonds as well as other similar movie.

;; If we try with old bond:

#_(return-fields
    [:title :score :release_date]
    (query-mlt-custom
      {:mlt.q  (str "db_id:" (:db_id bond-never-movie))
       :mlt.qf [["genres" 5] ["overview" 6] ["title" 3] ["tagline" 1] ["keywords" 1]]
       :now    (inst-ms (:release_date bond-never-movie))
       :bf     ["recip(sub(${now},ms(release_date)),3.16e-11,1,1)^2.5"
                "if(gt(popularity,50),1,0)^10"]}))

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
       :now   (inst-ms (:release_date bond-spectre-movie))
       :bf    ["recip(sub(${now},ms(release_date)),3.16e-11,1,1)^2"
               "if(gt(popularity,50),1,0)^10"]
       :rq    "{!ltr model=ltrGoaModel reRankDocs=40 efi.gender=1 efi.age=20 efi.occupation=1}"}))


;;; Now let's try with other user profile.

#_(return-fields
    [:title :score :release_date]
    (query-mlt-custom
      {:mlt.q (str "db_id:" (:db_id bond-spectre-movie))
       :now   (inst-ms (:release_date bond-spectre-movie))
       :bf    ["recip(sub(${now},ms(release_date)),3.16e-11,1,1)^2"
               "if(gt(popularity,50),1,0)^10"]
       :rq    "{!ltr model=ltrGoaModel reRankDocs=40 efi.gender=0 efi.age=60 efi.occupation=7}"}))

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

;; DATASET: users




;;; 8. Prepare training and testing datasets

(comment

  ;; Extract features

  (count (ml/extract-mov-features {:type :http :core :tmdb} "tmdb_features"))

  (first (ml/extract-mov-features {:type :http :core :tmdb} "tmdb_features"))

  ;; Build dataset

  ;; careful 10-15 mins
  #_(def ds (ml/build-training-dataset))                      ;; (ml/build-training-dataset 100 100)

  #_(first ds)

  #_(count ds)                                                ;; ~577k

  ;; Check ds balance (incanter)

  #_(icore/view (icharts/histogram (mapv (comp first :score) ds) :nbins 100))

  ;; dataset is unbalanced
  ;; we do need normalize it since we have:
  ;; - huge values for budget revenue etc
  ;; - unbalanced dataset


  ;; Normalize dataset

  (def normed (ml/normalize-training-dataset ds))

  (def normed-ds (:ds normed))

  (count normed-ds)                                         ; ~ 290k

  ;; to see what changed
  #_(icore/view (icharts/histogram (mapv (comp first :score) normed-ds) :nbins 100))


  ;; Shuffle dataset

  (def shuffled-ds (shuffle normed-ds))                     ; with starting MSE ~2


  (def splitted-ds (ml/split-dataset shuffled-ds))

  ;; Prepare train\test sets
  (defn upload-train-and-test-datasets!
    [splitted-dataset]
    (let []
      (spit (str data-dir "train.txt") (vec (:train splitted-dataset)))
      (spit (str data-dir "test.txt") (vec (:test splitted-dataset)))))

  (upload-train-and-test-datasets! splitted-ds)


  ;; cortex training
  ;; careful Hours!
  #_(cortex/train! (:train splitted-ds) (:test splitted-ds) 1000 "ltr-goa-nn")

  #_(def trained-net (cortex/load-trained-net "resources/solr/tmdb/data/models/ltr-goa-nn-1m-1_385mse.nippy"))
  #_(-> splitted-ds :train count)
  #_(-> splitted-ds :test count)
  #_(cortex/compare-results trained-net (:test splitted-ds) 100)
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
  (def test-ds (utils/read-file (str data-dir "test.txt")))

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
  (cortex/compare-results trained-net (:test splitted-ds) 100)
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
