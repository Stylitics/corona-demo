# corona-demo
A basic movie search and recommendation engine implementation that uses [Corona](https://github.com/Stylitics/corona) (a Solr 8 Clojure wrapper), ML (with Cortex, MXNet soon) and that exploits MLT (More Like This) handler and LTR (learning to rank) plugin.

## Prerequisites

### 1. Setup Solr

Please follow these instructions: 
https://github.com/Stylitics/corona/blob/master/doc/Installation.md

### 2. Setup Cortex (or MXNet)

#### Cortex setup instructions

https://github.com/originrose/cortex#gpu-compute-install-instructions

#### MXNet setup instructions

Soon. 

## Datasets

The datasets located in `resources/solr/tmdb/data/csv/` come from these sources:
* TMDB https://www.kaggle.com/tmdb/tmdb-movie-metadata/metadata
* GroupLens / MovieLens https://grouplens.org/datasets/movielens/

## Contributors

- Leon Talbot
- Alexey Makurin
- Arthur Caillau

## License

This library is available to use under MIT license.
