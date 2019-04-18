# Movie search demo project 

The idea of this project is to personalize movie search using user's profile (age, gender, occupation)

Intermediate goals \ steps are following:
- Build movies dataset with ratings and user profiles
- Design features to use for prediction of user's movie-score
- Design, train and test a NN model
- Import designed LTR features to SOLR
- Import trained NN to SOLR

## Dataset

Dataset for this project was composed using The Movie DB API, and the MovieLens datasets:
- `tmdb_5000_movies.csv` contains metadata for top 5000 movies of TMDB.
- `ratings.csv` and `users.csv` contains 1 million ratings from 6000 users (with profiles) on 4000 movies. Imported from [MovieLens 1M]
- `links.csv` contains correspondence of MovieLens ID with TMDB ID and IMDB ID for 46000 movies. Imported from [MovieLens 20M]

So, we've got consistent dataset with movies' metadata, ratings and user profiles.

## Feature Engineering 

Having raw dataset we need a way to extract pieces of information potentially important for and applicable to Machine Learning.
In ML such pieces are called features. So the feature is an aspect (or piece) of data entity that can be numerically coded and used as input to ML algorithm. 
A good example of feature is a pixel in a task of image recognition. Each pixel is a numerically coded piece of information about image. Pixels has spatial connections - every pixels has few neighboring pixels. This allows to use 2D convolutions to much better learning general primitives like borders etc.

In our case for movies we used following features:
- **genres**: we associated each possible genre with feature named `has_genres_[genre]` which takes value of 1 if movie is of this genre and 0 if it is not. 
For example, the movie "Iron Man" is of genre "action" and not of genre "romantic" so vector of genre features for this movie contains `has_genres_action` = 1 and all other components, including `has_genres_romantic`, are set to 0. 
- **production countries**: similar to genres
- **spoken languages**: similar to genres
- **popularity**: numeric value from raw dataset
- **vote_average**: numeric value from raw dataset
- **runtime**: numeric value from raw dataset
- **revenue**: numeric value from raw dataset
- **budget**: numeric value from raw dataset

200 movie features in total.

We need to upload description of these features to SOLR to specify how SOLR should build input vector for stored model.
We used following SOLR LTR feature classes:
- SolrFeature - executes function over document and uses function output as feature value. We used these to declare "collection" features like genres, production countries etc.
- FieldValueFeature uses value of document field as feature value. We used it for "simple" features like popularity, budget etc.
- ValueFeature which is used to provide some values with query. We use it to provide user's profile values: age, gender, occupation.

LTR feature descriptions are uploaded to SOLR in form of JSON array.
You can find functions generating description for these types of SOLR LTR features in `corona.ltr` namespace. 

The final JSON array of features is built by `corona-demo.ml.core/build-features`.

```
(ml/build-features "tmdb_features" data/movies)
;; result is a list of maps ready to JSON encoding

({:store "tmdb_features",
  :name "hasGenresAction",
  :class "org.apache.solr.ltr.feature.SolrFeature",
  :params {:q "{!func}termfreq(genres,'action')"}}
...
 {:store "tmdb_features",
  :name "hasGenresTv_movie",
  :class "org.apache.solr.ltr.feature.SolrFeature",
  :params {:q "{!func}termfreq(genres,'tv movie')"}}
  
 {:store "tmdb_features",
  :name "hasProduction_countriesUs",
  :class "org.apache.solr.ltr.feature.SolrFeature",
  :params {:q "{!func}termfreq(production_countries,'us')"}}
...
 {:store "tmdb_features",
  :name "hasProduction_countriesKe",
  :class "org.apache.solr.ltr.feature.SolrFeature",
  :params {:q "{!func}termfreq(production_countries,'ke')"}}
  
 {:store "tmdb_features",
  :name "hasSpoken_languagesEn",
  :class "org.apache.solr.ltr.feature.SolrFeature",
  :params {:q "{!func}termfreq(spoken_languages,'en')"}}
...
 {:store "tmdb_features",
  :name "hasSpoken_languagesFr",
  :class "org.apache.solr.ltr.feature.SolrFeature",
  :params {:q "{!func}termfreq(spoken_languages,'fr')"}}

 {:store "tmdb_features",
  :name "popularity",
  :class "org.apache.solr.ltr.feature.FieldValueFeature",
  :params {:field "popularity"}}
 {:store "tmdb_features",
  :name "vote_average",
  :class "org.apache.solr.ltr.feature.FieldValueFeature",
  :params {:field "vote_average"}}
 {:store "tmdb_features",
  :name "runtime",
  :class "org.apache.solr.ltr.feature.FieldValueFeature",
  :params {:field "runtime"}}
 {:store "tmdb_features",
  :name "revenue",
  :class "org.apache.solr.ltr.feature.FieldValueFeature",
  :params {:field "revenue"}}
 {:store "tmdb_features",
  :name "budget",
  :class "org.apache.solr.ltr.feature.FieldValueFeature",
  :params {:field "budget"}}
 {:store "tmdb_features",
  :name "gender",
  :class "org.apache.solr.ltr.feature.ValueFeature",
  :params {:value "${gender}", :required false}}
 {:store "tmdb_features",
  :name "occupation",
  :class "org.apache.solr.ltr.feature.ValueFeature",
  :params {:value "${occupation}", :required false}}
 {:store "tmdb_features",
  :name "age",
  :class "org.apache.solr.ltr.feature.ValueFeature",
  :params {:value "${age}", :required false}})

```

With these features SOLR LTR works as follows:

- for each document supposed to re-rank SOLR will extract all features of classes SolrFeature and FieldValueFeature, which results in vector of values. Then solr will concatenate this vector with values for features of type  ValueFeature provided with query, forming final input vector.
- this input vector is given as input to stored model, which will calculate corresponding output value
- resulting value is taken as a new rank for given document

Once uploaded, SOLR LTR feature descriptions can be used to extract features from existing documents using SOLR API (see `corona-demo.ml.core/extract-mov-features`)

We use this to build training and test datasets for our model on further steps.

Final feature vector size is 203x1x1. (200 movie features + 3 user's profile features)

## Model Architecture

The model has following task:
Taking as input full feature vector of given movie concatenated with user's profile data predict rating that user with this profile would give to this movie.

We don't know how important each feature is. We just want our model to learn somehow to predict user ratings. We have pretty large feature vector. So we decided to go with Neural Network.

Unlike pixels, movie features are not spatially connected initially, which means we can't use convolutions. So we went with just last few classification layers - pretty common in all modern cNN.  

The final model consists of the following layers:

| Layer         		|     Description	        					| 
|:---------------------:|:---------------------------------------------:| 
| Input         		| 203x1x1 (200 movie features + 3 user features)| 
| Classifier 			| 203 -> 512									|
| RELU					|												|
| Dropout				| train prob 0.5, eval prob 1.0					|
| Classifier			| 512 -> 128									|
| RELU					|												|
| Dropout				| train prob 0.5, eval prob 1.0					|
| Classifier			| 128 -> 16										|
| RELU					|												|
| Classifier			| 16 -> 1	Output score     				    |

Model includes 4 classifier layers, internal layers are followed by RELU to add nonlinearity and dropout (that drops some connections during training to prevent overfitting). Sizes are: 512 128 16 1. Output of last layer considered score prediction.

You can find model implementation using `cortex` lib in `corona-demo.ml.cortex/train!`

## Model training 

### Dataset Composition

To train the model we need training and test datasets. We compose these the same way as solr will build inputs for our model:
- extract features for documents uploaded to SOLR;
- join it to ratings dataset;
- join user features.

See `corona-demo.ml.core/build-training-dataset` for implementation.

```
(ml/build-training-dataset)
;; each entry contains 
;; feature vector (same as SOLR will compose)
;; score for this feature vector 
;; and additional info to better understand where this entry came from
[{:movieId 1945,
  :movieIdTMDB 654,
  :userId 2,
  :score [5.0],
  :features [0.0 0.0 0.0 0.0 1.0 1.0 0.0 0.0 0.0 0.0 0.0 0.0 
  0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 1.0 0.0 0.0 0.0 0.0 0.0 0.0 
  0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
  0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
  0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
  0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
  0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
  0.0 0.0 0.0 0.0 0.0 0.0 1.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
  0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
  0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
  0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
  0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
  0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
  0.0 0.0 0.0 16.015598 8.0 108.0 9600000.0 910000.0 1.0 56.0 
  16.0]}
 ...]
```

As a result we've got labeled dataset of feature vectors, where each feature vector is our 200m+3u and label is this user's rating for this movie.

The size of dataset is: ~577k

### Dataset Normalization 

Although this dataset can be used as is, it unlikely give good results during training. There are following reasons for this:
- feature vector values are of different scale (eg. budget is a value in range 10mil to 300mil, popularity is in range 0 to 150, genres are 0 or 1 etc.);
- dataset is heavily unbalanced (e.g. there are very few ratings of 2 or 2.5 but there are huge amount of ratings with value 5).

We can fix first issue by scaling all feature values to range [0..1]. To do this we can set meaningful minimum and maximum values manually for each feature, or we can just find this values analyzing our dataset, which is what we do.

The easiest way to fix second issue is to balance dataset using oversampling and undersampling technique. We synthesize entries with rare rating values (oversampling) and skip entries with too frequent ratings (undersampling).

Original Dataset
![comp-ds]

Normalized Dataset
![norm-ds]

Normalized dataset size is ~295k

See `corona-demo.ml.core/normalize-training-dataset`  for implementation.

```
(ml/normalize-training-dataset ds)
;; returns map of :ds - normailized dataset
;; :mins and :maxs - minimum and maximum values of 8 last features (those whose values > 1) 
;; this :mins and :maxs will be used later as params of Normalizer in SOLR
{
:mins [0.003142 0.0 70.0 0.0 0.0 0.0 18.0 0.0]
:maxs [146.75739 10.0 242.0 1.84503424E9 2.0E8 1.0 56.0 20.0]
:ds [{:movieId 2268,
     :movieIdTMDB 881,
     :userId 2,
     :score [5.0],
     :features (0.0 0.0 0.0 0.0 0.0 1.0 0.0 
     0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 0.0 0.0 1.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 1.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 
     0.2751322472224754 0.7099999904632568 
     0.3953488372093023 0.1318350471371198 
     0.2 1.0 1.0 0.8)}
     ...]
}

```

### Training

To train and test our model we shuffle dataset and split it onto 2 parts with ratio 0.8

So we've got training dataset of size ~234k and test dataset of size ~58k.

The model trained over 1000 epochs with batch size 200 using Adam optimizer.

See `corona-demo.ml.cortex` namespace for implementation.

## Model uploading

To use our model with SOLR it has to be uploaded in SOLR readable format. 

SOLR model description consists of model parameters, input features enumeration (these are just references to feature descriptions uploaded earlier), with optional normalizer parameters. 

First, we need to extract model data from Cortex\MXNet.
See `corona-demo.ml.core/get-layers` for Cortex implementation. Cortex uses custom type to store matrices (called Datatype), so to extract weights and biases of our model we need to deal with it.

Then we generate input features enumeration using features generated above adding MinMaxNormalizer params.

We use MinMaxNormalizer to get same effect of scaling that we got during dataset normalization. For this to work we provide same minimas and maximas that we used before.

See 
- `corona-demo.ml.core/build-nn-model` 
- `corona.ltr/build-solr-ltr-nn-model`
- `corona.ltr/prepare-features-for-nn`

```
(ml/build-nn-model trained-net normed movie-features)
;; returns map ready to be JSON encoded and uploaded to SOLR
{:store "tmdb_features",
 :name "ltrGoaModel",
 :class "org.apache.solr.ltr.model.NeuralNetworkModel",
 :features [{:name "hasGenresAction"}
            {:name "hasGenresAdventure"}
...
            {:name "hasGenresTv_movie"}
            {:name "hasProduction_countriesUs"}
...
            {:name "hasProduction_countriesKe"}
            {:name "hasSpoken_languagesEn"}
...
            {:name "hasSpoken_languagesSl"}
            {:name "popularity",
             :norm {:class "org.apache.solr.ltr.norm.MinMaxNormalizer", :params {:min "0.003142", :max "146.75739"}}}
            {:name "vote_average",
             :norm {:class "org.apache.solr.ltr.norm.MinMaxNormalizer", :params {:min "0.0", :max "10.0"}}}
            {:name "runtime",
             :norm {:class "org.apache.solr.ltr.norm.MinMaxNormalizer", :params {:min "70.0", :max "242.0"}}}
            {:name "revenue",
             :norm {:class "org.apache.solr.ltr.norm.MinMaxNormalizer", :params {:min "0.0", :max "1.84503424E9"}}}
            {:name "budget",
             :norm {:class "org.apache.solr.ltr.norm.MinMaxNormalizer", :params {:min "0.0", :max "2.0E8"}}}
            {:name "gender",
             :norm {:class "org.apache.solr.ltr.norm.MinMaxNormalizer", :params {:min "0.0", :max "1.0"}}}
            {:name "occupation",
             :norm {:class "org.apache.solr.ltr.norm.MinMaxNormalizer", :params {:min "18.0", :max "56.0"}}}
            {:name "age", :norm {:class "org.apache.solr.ltr.norm.MinMaxNormalizer", :params {:min "0.0", :max "20.0"}}}],
 :params {:layers [{:matrix [[-0.39864271879196167
                              -0.21664874255657196
... ~ 170k of values
                              0.10659924149513245
                              0.0954180434346199
                              0.009702344425022602
                              0.0025878273881971836
                              -0.0985892042517662
                              0.11178715527057648
                              -0.16201117634773254
                              0.2953701317310333]],
                    :bias [0.8357605934143066
                           0.2845083475112915
                           0.9224351048469543
                           0.9197794795036316
                           0.3681538701057434
                           0.45611727237701416
                           0.9280017614364624
                           0.9132157564163208
                           0.15061746537685394
                           0.2997820973396301
                           0.6084812879562378
                           0.7483801245689392
                           0.25783365964889526
                           0.917015016078949
                           0.8256568908691406
                           0.935774564743042],
                    :activation :relu}
                   {:matrix [[0.36546602845191956
                              -0.5063765048980713
                              0.22763343155384064
                              0.24993960559368134
                              -0.38769492506980896
                              -0.6524369120597839
                              0.504414975643158
                              0.42825087904930115
                              -0.6906170845031738
                              -0.4974370300769806
                              0.3323124349117279
                              -0.5071088075637817
                              -0.4687376618385315
                              0.34768763184547424
                              0.30988016724586487
                              0.5404917597770691]],
                    :bias [0.9016101360321045],
                    :activation :identity}]}}
```

Finally prepared model can be uploaded to SOLR using `corona.ltr/upload-model!`

## Using model for re-ranking

Uploaded model can be used in any SOLR query that supports re-rank (customized MoreLikeThis is one of them).

To use model for re-rank add following key to your query:
```
{:rq "{!ltr model=ltrGoaModel reRankDocs=40 efi.gender=1 efi.age=20 efi.occupation=1}}
``` 
Here 
- `rq` is short for "Re-rank Query"
- `model=ltrGoaModel` - name of uploaded model
- `reRankDocs=40` - number of documents to re-rank
- `efi.gender=1 efi.age=20 efi.occupation=1` - values for user's profile features that are passed with query.


[MovieLens 1M]: https://grouplens.org/datasets/movielens/1m/
[MovieLens 20M]: https://grouplens.org/datasets/movielens/20m/

[comp-ds]: ./composed-ds.png "Original Dataset" 
[norm-ds]: ./normalized-ds.png "Normalized Dataset"