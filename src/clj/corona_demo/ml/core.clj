(ns corona-demo.ml.core
  (:require
   [clojure.data.json :as json]
   [clojure.string :as string]
   [corona.ltr :as ltr]
   [corona-demo.data :as data]
   [corona-demo.utils :as utils]
   [think.datatype.core :as dtype]))


#_(first (data/read-users))
#_(first (data/read-ratings))

(defn build-features
  "Builds all project features for solr ltr.
  'store-name' - string name of store to specify for features (defaults to \"tmdb_features\")."
  [store-name movies]
  (mapcat identity
          [(ltr/gen-coll-features movies :genres store-name)
           (ltr/gen-coll-features movies :production_countries store-name)
           (ltr/gen-coll-features movies :spoken_languages store-name)
           ;; too many tags to use...

           [(ltr/gen-field-feature :popularity store-name)
            (ltr/gen-field-feature :vote_average store-name)
            (ltr/gen-field-feature :runtime store-name)
            (ltr/gen-field-feature :revenue store-name)
            (ltr/gen-field-feature :budget store-name)

            ;; user features
            (ltr/gen-external-value-feature :gender false store-name)
            (ltr/gen-external-value-feature :occupation false store-name)
            (ltr/gen-external-value-feature :age false store-name)]]))


#_(ltr/upload-features {:type :http :core :tmdb} (build-features "tmdb_features" [{}]))
#_(ltr/delete-feature-store {:type :http :core :tmdb})

(defn extract-mov-features
  "Extract features from solr
  executing HTTP GET request with query to 'sorl-core-url' (or http://localhost:8983/solr/tmdb).
  'store-name' - string name of store to extract features from (defaults to tmdb_features).
  Returns vector of maps with keys:
  :db_id - id of movie;
  :features - vector of features values for movie."
  [client-config store-name]
  (ltr/extract-features
   client-config
   {:q "*:*"
    :rows "10000"
    :sort "db_id asc"
    :fl "db_id"
    :store store-name}))




(defn build-training-dataset
  "Builds dataset to use for train/test.
  Returns vector of maps with keys:
  :movieId, :userId, :score, :features
  where :features is vector of movie features concatenated with [user-gender user-age user-occupation]."
  []
  (let [users (data/read-users)
        mov-feats (extract-mov-features {:type :http :core :tmdb}
                                        "tmdb_features")]
    (->> (data/read-ratings)
         (map (fn [{:keys [userId movieId] :as rating}]
                (-> rating
                    (assoc :user  (first (filter #(= (:userId %) userId) users)))
                    (assoc :movie (first (filter #(= (:db_id %) (str movieId)) mov-feats))))))
         (remove (comp nil? :user))
         (remove (comp nil? :movie))
         (map (fn [{:keys [userId rating user movie]}]
                {:movieId  (Long/parseLong (:db_id movie))
                 :userId   userId
                 :score    [rating]
                 :features (vec
                            (concat
                             (drop-last 3 (:features movie))
                             [(:gender user) (:age user) (:occupation user)]))}))
         (doall))))

#_(build-training-dataset)

(defn normalize-training-dataset
  "Normalizes last 8 features (popularity vote_average runtime revenue budget gender occupation age) of dataset using MinMax norm.
  Performs simple undersampling for rates 3.0 4.0 5.0.
  Performs simple oversampling for rates 0.5 1.0 1.5.
  Returns map with keys
  :ds - normalized dataset with same structure as original;
  :mins - vector of minimums for last 8 features;
  :maxs - vector of maximums for last 8 features."
  [ds]
  (let [to-norm-count 8
        to-norm (->> ds
                     (map :features)
                     (map (partial take-last to-norm-count)))
        maxs (mapv (fn [index]
                     (->> to-norm
                          (map #(nth % index))
                          (reduce max 0)))
                   (range to-norm-count))
        mins (mapv (fn [index]
                     (->> to-norm
                          (map #(nth % index))
                          (reduce min Long/MAX_VALUE)))
                   (range to-norm-count))
        deltas (map - maxs mins)]
    {:ds   (->> ds
                (map #(update % :features ltr/do-to-last-n to-norm-count
                              (fn [index feat] (/ (- feat (nth mins index)) (nth deltas index)))))
                ;; dumb undersampling
                (remove (fn [{:keys [score]}] (and (= (first score) 3.0) (< (rand-int 10) 6))))
                (remove (fn [{:keys [score]}] (and (= (first score) 4.0) (< (rand-int 10) 7))))
                (remove (fn [{:keys [score]}] (and (= (first score) 5.0) (< (rand-int 10) 0))))
                ;; dumb oversampling
                (reduce (fn [ds entry]
                          (cond
                            (< (-> entry :score first) 1.0)
                            (vec (concat ds [entry entry entry entry]))
                            (< (-> entry :score first) 1.1)
                            (vec (concat ds [entry entry]))
                            (< (-> entry :score first) 1.6)
                            (vec (concat ds [entry entry entry entry]))
                            :else (conj ds entry)))
                        [])
                vec)
     :mins mins
     :maxs maxs}))

(defn get-layers
  "Extracts layers (weights and biases) of Cortex trained network 'cortex-trained-nn' in ready-for-json form.
  Returns vector of layers."
  [cortex-trained-nn]
  (let [{:keys [edges nodes buffers]} (-> cortex-trained-nn :compute-graph)]
    (->> edges
         (map (fn [[_ lay2k]]
                (let [lay2 (get nodes lay2k)
                      out-size (:output-size lay2)
                      in-size (-> lay2 :input-dimensions first :width)
                      w-buffer-id (-> lay2 :weights :buffer-id)
                      b-buffer-id (-> lay2 :bias :buffer-id)
                      w-bufs (get-in buffers [w-buffer-id :buffer])
                      b-buf (get-in buffers [b-buffer-id :buffer])]

                  (when (-> lay2 :type #{:linear})
                    {:matrix    (if (vector? w-bufs)
                                  (mapv (fn [buf]
                                          (mapv (fn [offset]
                                                  (dtype/get-value buf offset))
                                                (range in-size)))
                                        w-bufs)
                                  [(mapv (fn [offset]
                                           (dtype/get-value w-bufs offset))
                                         (range in-size))])

                     :bias       (mapv (fn [offset] (dtype/get-value b-buf offset))
                                       (range out-size))
                     :activation :relu}))))

         (remove nil?)
         vec
         (#(assoc-in % [(-> % count dec) :activation] :identity)))))

#_(get-layers trained-net)

#_(build-solr-ltr-model "tmdb_features" "ltrGoaModel" (get-layers trained-net) (:mins normed) (:maxs normed))

#_(ltr/delete-model! "ltrGoaModel")

(defn build-nn-model
  [trained-net normed movie-features]
  (ltr/build-solr-ltr-nn-model
   "tmdb_features"
   "ltrGoaModel" ;Gender Occupation Age
   (map #(select-keys % [:name]) movie-features)
   (get-layers trained-net)
   (:mins normed)
   (:maxs normed)))
