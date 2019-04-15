(ns corona-demo.ml.mxnet
  (:require
   [corona-demo.utils :as utils]
   [org.apache.clojure-mxnet.callback :as callback]
   [org.apache.clojure-mxnet.context :as context]
   [org.apache.clojure-mxnet.dtype :as d]
   [org.apache.clojure-mxnet.eval-metric :as eval-metric]
   [org.apache.clojure-mxnet.executor :as executor]
   [org.apache.clojure-mxnet.initializer :as initializer]
   [org.apache.clojure-mxnet.io :as mx-io]
   [org.apache.clojure-mxnet.module :as m]
   [org.apache.clojure-mxnet.ndarray :as ndarray]
   [org.apache.clojure-mxnet.optimizer :as optimizer]
   [org.apache.clojure-mxnet.symbol :as sym]
   [org.apache.clojure-mxnet.visualization :as viz]
   [corona-demo.ml.mxnet :as mxnet]
   [corona-demo.data :as data]))


;; Defining the computation graph of the Model

(defn get-symbol
  []
  (as-> (sym/variable "data") data
    (sym/fully-connected "fc1" {:data data :num-hidden 512})
    (sym/activation "act1" {:data data :act-type "relu"})
    (sym/dropout "drop1" {:data data :p 0.5})
    (sym/fully-connected "fc2" {:data data :num-hidden 128})
    (sym/activation "act2" {:data data :act-type "relu"})
    (sym/dropout "drop2" {:data data :p 0.5})
    (sym/fully-connected "fc3" {:data data :num-hidden 16})
    (sym/activation "act3" {:data data :act-type "relu"})
    (sym/fully-connected "fc4" {:data data :num-hidden 1})
    (sym/linear-regression-output "linear_regression" {:data data})))

;; Wrapping the computation graph in a `module`
(def model-mod
  (m/module (get-symbol)
            {:data-names ["data"]
             :label-names ["linear_regression_label"]}))

;; Model Vizualisation

(defn render-model!
  "Render the `model-sym` and saves it as a png file in `path/model-name.png`
  using graphviz."
  [{:keys [model-name model-sym input-data-shape path]}]
  (let [dot (viz/plot-network
              model-sym
              {"data" input-data-shape}
              {:title model-name
               :node-attrs {:shape "oval" :fixedsize "false"}})]
    (viz/render dot "dot" "png" model-name path)))

;; Creating Datasets as NDArrays

(defn dataset->X-Y
  [dataset]
  (let [n-datapoints (count dataset)
        n-features (-> dataset first :features count)]
    {:X (ndarray/array (->> dataset (map :features) flatten (into []))
                       [n-datapoints n-features])
     :Y (ndarray/array (->> dataset (map :score) flatten (into []))
                       [n-datapoints 1])}))



(defonce test-X-Y
  (-> (str data/data-dir "test.txt")
      utils/read-file
      dataset->X-Y))

(defonce train-X-Y
  (-> (str data/data-dir "train.txt")
      utils/read-file
      dataset->X-Y))

(def batch-size 2000)

(def test-iter
  (mx-io/ndarray-iter [(get test-X-Y :X)]
                      {:label-name "linear_regression_label"
                       :label [(get test-X-Y :Y)]
                       :data-batch-size batch-size}))

(def train-iter
  (mx-io/ndarray-iter [(get train-X-Y :X)]
                      {:label-name "linear_regression_label"
                       :label [(get train-X-Y :Y)]
                       :data-batch-size batch-size}))

(defn train!
  [model-mod train-iter test-iter num-epoch]
  (-> model-mod
      (m/bind {:data-shapes (mx-io/provide-data train-iter)
               :label-shapes (mx-io/provide-label test-iter)})
      (m/fit {:train-data train-iter
              :eval-data test-iter
              ;; Training for `num-epochs`
              :num-epoch num-epoch
              :fit-params
              (m/fit-params
                {:batch-end-callback (callback/speedometer batch-size 100)
                 ;; Initializing weights with Xavier
                 :initializer (initializer/xavier)
                 ;; Choosing Optimizer Algorithm: SGD with lr = 0.01
                 :optimizer (optimizer/sgd {:learning-rate 0.01})
                 ;; Evaluation Metric
                 :eval-metric (eval-metric/mse)})})))

(defn train
  [num-epoch]
  (mxnet/train! model-mod train-iter test-iter num-epoch))


(defn score
  "Evaluates performance"
  []
  (m/score model-mod
           {:eval-data test-iter
            :eval-metric (eval-metric/mse)}))

(defn render-model
  "Rendering model as `mymodel.png`"
  [path]
  (render-model! {:model-name "mymodel"
                  :model-sym (get-symbol)
                  :input-data-shape [1 203 1 1]
                  :path path}))


