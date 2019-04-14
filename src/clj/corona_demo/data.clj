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
