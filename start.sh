#!/usr/bin/env bash

source env_vars;
$SOLR_HOME/bin/solr stop -p 8983
$SOLR_HOME/bin/solr start -p 8983
lein clean
lein repl
