(ns corona-demo.data
  (:require [clojure.data.json :as json]
            [corona-demo.utils :as utils]))


;;; Movies

(def tmdb-csv-path (str (System/getProperty "user.dir") "/resources/solr/tmdb/data/csv/"))

(def movies-file "tmdb_5000_movies.csv")

(def movies-fields-val-fns
  {:genres (utils/mapv-json-vals "name")
   :keywords (utils/mapv-json-vals "name")
   :spoken_languages (utils/mapv-json-vals "iso_639_1")
   :production_companies (utils/mapv-json-vals "name")
   :production_countries (utils/mapv-json-vals "iso_3166_1")})

(def movies*
  (utils/read-csv
   (str tmdb-csv-path movies-file)
   {:key-fn keyword :val-fns movies-fields-val-fns}))


(def credits-file "tmdb_5000_credits.csv")

(def credits-raw-maps
  (utils/read-csv
   (str tmdb-csv-path credits-file)
   {:key-fn keyword}))

(defn parse-credit-raw-map
  [{:keys [movie_id cast crew] :as row}]
  (let [crew-data (json/read-str crew)]
    {:db_id (:movie_id row)
     :cast ((utils/mapv-json-vals "name") cast)
     :director (-> (filter #(= "Director" (get % "job")) crew-data)
                   first
                   (get "name"))
     :producers (->> crew-data
                     (filter #(= "Producer" (get % "job")))
                     (mapv #(get % "name")))}))

(defonce credits (map parse-credit-raw-map credits-raw-maps))

(defonce movies (utils/merge-by :db_id movies* credits))




(def schema-type-text_en_splitting
  "A text field with defaults appropriate for English, plus
  aggressive word-splitting and autophrase features enabled.
  This field is just like text_en, except it adds
  WordDelimiterGraphFilter to enable splitting and matching of
  words on case-change, alpha numeric boundaries, and
  non-alphanumeric chars.  This means certain compound word
  cases will work, for example query \"wi fi\" will match
  document \"WiFi\" or \"wi-fi\".
  "
  {:name "text_en_splitting"
   :class "solr.TextField"
   :positionIncrementGap "100"
   :autoGeneratePhraseQueries "true"
   :indexAnalyzer {:tokenizer {:class "solr.WhitespaceTokenizerFactory"}
                   :filters [
                             ;; Removes stop words
                             {:class "solr.StopFilterFactory"
                              :words "lang/stopwords_en.txt" ;added
                              :ignoreCase "true"}

                             ;; Splits and matches words on case-change,
                             ;; alpha numeric boundaries, and
                             ;; non-alphanumeric chars
                             ;; 'wi fi' = 'WiFi' = 'wi-fi'
                             {:class "solr.WordDelimiterGraphFilterFactory"
                              :generateWordParts "1"
                              :generateNumberParts "1"
                              :catenateWords "1"
                              :catenateNumbers "1"
                              :catenateAll "0"
                              :splitOnCaseChange "1"}

                             ;; Lowercases content
                             {:class "solr.LowerCaseFilterFactory"}

                             ;; Protects some words from being stemmed
                             {:class "solr.KeywordMarkerFilterFactory"
                              :protected "protwords.txt"}

                             ;; Applies the Porter Stemming Algorithm for English
                             ;; "jump" "jumping" "jumped" => "jump" "jump" "jump"
                             {:class "solr.PorterStemFilterFactory"}

                             ;; Needed for synonyms
                             {:class "solr.FlattenGraphFilterFactory"}]}
    :queryAnalyzer {:type "query"
                    :tokenizer {:class "solr.WhitespaceTokenizerFactory"}

                    :filters [ ;; Adds up in synonyms at query time
                              {:class "solr.SynonymGraphFilterFactory"
                               :synonyms "synonyms.txt" ;added
                               :ignoreCase "true"
                               :expand "true"}
                              {:class "solr.StopFilterFactory"
                               :words "lang/stopwords_en.txt" ;added
                               :ignoreCase "true"}
                              {:class "solr.WordDelimiterGraphFilterFactory"
                               :generateWordParts "1"
                               :generateNumberParts "1"
                               :catenateWords "1"
                               :catenateNumbers "1"
                               :catenateAll "0"
                               :splitOnCaseChange "1"}
                              {:class "solr.LowerCaseFilterFactory"}
                              {:class "solr.KeywordMarkerFilterFactory"
                               :protected "protwords.txt" ;added
                               }
                              {:class "solr.PorterStemFilterFactory"}
                              ]}})


(def basic-fields
  "You can find your basic schema types here:
  resources/solr/tmdb/conf/managedschema"
  [{:name "_text_",
    :type "text_en_splitting",
    :multiValued true,
    :indexed true,
    :stored false}

   {:name "_version_", ; version of document, query per version
    :type "plong",
    :indexed true,
    :stored true}

   {:name "db_id",
    :type "string",
    :multiValued false,
    :indexed true,
    :required true,
    :stored true}])

(def content-fields
  [{:name "tagline",
    :type "text_en_splitting",
    :indexed true,
    :stored true
    :termVectors true}

   {:name "overview",
    :type "text_en_splitting",
    :indexed true,
    :stored true
    :termVectors true}

   {:name "genres",
    :type "text_lowcased",
    :multiValued true,
    :indexed true,
    :stored true
    :termVectors true}

   {:name "title",
    :type "text_lowcased",
    :indexed true,
    :stored true
    :termVectors true}

   {:name "keywords",
    :type "text_en_splitting",
    :indexed true,
    :stored true
    :multiValued true
    :termVectors true}

   {:name "original_title",
    :type "text_lowcased",
    :indexed true,
    :stored true
    :termVectors true}

   {:name "production_companies",
    :type "text_lowcased",
    :multiValued true,
    :indexed true,
    :stored true
    :termVectors true}

   {:name "production_countries",
    :type "text_lowcased",
    :multiValued true,
    :indexed true,
    :stored true
    :termVectors true}

   {:name "spoken_languages",
    :type "text_lowcased",
    :multiValued true,
    :indexed true,
    :stored true
    :termVectors true}

   {:name "original_language",
    :type "text_lowcased",
    :indexed true,
    :stored true
    :termVectors true}

   {:name "status",
    :type "text_lowcased",
    :indexed true,
    :stored true
    :termVectors true}

   {:name "homepage",
    :type "text_lowcased",
    :indexed true,
    :stored true
    :termVectors true}

   {:name "cast",
    :type "text_lowcased",
    :indexed true,
    :stored true
    :multiValued true
    :termVectors true}

   {:name "director",
    :type "text_lowcased",
    :indexed true,
    :stored true
    :termVectors true}

   {:name "producers",
    :type "text_lowcased",
    :indexed true,
    :stored true
    :multiValued true
    :termVectors true}])

(def number-fields
  [{:name "release_date",
    :type "pdate",
    :indexed true,
    :stored true}

   {:name "budget",
    :type "plong",
    :indexed true,
    :stored true}

   {:name "revenue",
    :type "plong",
    :indexed true,
    :stored true}

   {:name "vote_count",
    :type "plongs"}

   {:name "popularity",
    :type "pfloat",
    :indexed true,
    :stored true}

   {:name "runtime",
    :type "pfloat",
    :indexed true,
    :stored true}

   {:name "vote_average",
    :type "pfloat",
    :indexed true,
    :stored true}])
