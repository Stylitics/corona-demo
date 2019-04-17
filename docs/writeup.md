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
[TODO]

[MovieLens 1M]: https://grouplens.org/datasets/movielens/1m/
[MovieLens 20M]: https://grouplens.org/datasets/movielens/20m/