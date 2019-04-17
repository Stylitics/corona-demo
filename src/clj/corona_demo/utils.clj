(ns corona-demo.utils
  (:require
   [clojure.data.csv :as csv]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]))


(defn mapv-json-vals
  "Usage: see [[clj-csv-utils.test.utils]]"
  [k]
  (fn [s]
    (mapv #(get % (name k)) (json/read-str s))))


(defn mappify-csv-data
  "Takes a sequence of row vectors, and returns a sequence of maps."
  [rows & [{:keys [key-fn kv-fns kv-fn val-fns val-fn]
            :or {key-fn keyword}}]]
  (let [headers (first rows)
        rrows   (rest rows)
        combine-fn (fn [header cell]
                     (cond-> [(key-fn header) cell]
                       (or kv-fns kv-fn)
                       ((fn [[k v]] ((or (get kv-fns k) kv-fn identity) [k v])))

                       (or val-fns val-fn)
                       ((fn [[k v]] [k ((or (get val-fns k) val-fn identity) v)]))))]

    (reduce (fn [maps row]
              (conj maps (into {} (mapv combine-fn headers row))))
            []
            rrows)))


(defn read-csv
  [filename & [csv-opts]]
  (with-open [reader (-> filename io/reader)]
    (mappify-csv-data (csv/read-csv reader) csv-opts)))

(defn read-csv-resource
  [filename & [csv-opts]]
  (read-csv (io/resource filename) csv-opts))

(defn read-file
  [filename]
  (-> filename
      slurp
      read-string))
