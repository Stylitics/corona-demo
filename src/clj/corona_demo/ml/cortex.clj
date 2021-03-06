(ns corona-demo.ml.cortex
  (:require
   [cortex.experiment.train :as train]
   [cortex.nn.execute :as execute]
   [cortex.nn.layers :as layers]
   [cortex.nn.network :as network]))


(defn load-trained-net [filename]    (train/load-network filename))

(defn run-trained-net  [net entries] (execute/run net entries))

(defn train!
  "Builds classifier network as 4-layered perception, trains it using 'train-set' and 'test-set'.
  Returns map representation of trained network."
  [train-set test-set num-epoch out-filename]
  (train/train-n
   (network/linear-network
    [(layers/input 203 1 1 :id :features)
     (layers/linear 512)
     (layers/relu)
     (layers/dropout 0.5)
     (layers/linear 128)
     (layers/relu)
     (layers/dropout 0.5)
     (layers/linear 16)
     (layers/relu)
     (layers/linear 1 :id :score)])
   train-set
   test-set
   :batch-size 200
   :network-filestem out-filename ; provide no extension, .nippy will be appended.
   :epoch-count num-epoch))

(defn compare-results [trained-net test-dataset & [num-to-take]]
  (let [num-to-take (or num-to-take 30)]
    (->> test-dataset
       (remove (fn [{:keys [score]}]
                 ;; skip mid range ratings as not so interesting
                 (and (> (first score) 2.1) (< (first score) 5.0))))
       (take num-to-take)
       (map (fn [entry]
              {:act-score (-> entry :score first)
               :net-score (-> (run-trained-net trained-net [entry])
                              first
                              :score
                              first)})))))

(comment

  ;; A. load already trained NN from file:
  
  (def trained-net (load-trained-net "ltr-goa-nn-.nippy"))

  ;; B. train NN
  (train! (:train splitted-ds) (:test splitted-ds) 1000 "ltr-goa-nn-new")
  ;; Then load newly trained model:
  (def trained-net (load-trained-net "ltr-goa-nn-new.nippy"))

  ;; to see what scores did we predict for top 30 entries of test set (excluding entries with mid ranges)
  (compare-results trained-net (:test splitted-ds))
  )
