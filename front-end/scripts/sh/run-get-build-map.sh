#!/bin/sh
# Run a query to build map
# Assume DBB env variables are set
# Activate db2 MetadataStore, BuildGroup, and BuildMap classes from pulizia-cassaforte.jar
export JAVA_OPTS=-Dsun.jnu.encoding=IBM-1047
groovyz -cp ../build/libs/pulizia-cassaforte.jar groovy/GetBuildMap.groovy