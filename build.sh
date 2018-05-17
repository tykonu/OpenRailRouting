#! /usr/bin/env bash

set -euo pipefail

BASEDIR=$(pwd)
rm -rf maven_repository/*
cd graphhopper
mvn --projects web,core,reader-osm -P include-client-hc -am -DskipTests=true compile package
# mvn deploy:deploy-file -Durl=file://$(pwd)/../maven_repository/ -Dfile=core/target/graphhopper-core-0.11-SNAPSHOT.jar -DgroupId=com.graphhopper -DartifactId=forked-graphhopper-core -Dpackaging=jar -Dversion=0.11.0-SNAPSHOT
# mvn deploy:deploy-file -Durl=file://$(pwd)/../maven_repository/ -Dfile=web/target/graphhopper-web-0.11-SNAPSHOT.jar -DgroupId=com.graphhopper -DartifactId=forked-graphhopper-web -Dpackaging=jar -Dversion=0.11.0-SNAPSHOT
# mvn deploy:deploy-file -Durl=file://$(pwd)/../maven_repository/ -Dfile=reader-osm/target/graphhopper-reader-osm-0.11-SNAPSHOT.jar -DgroupId=com.graphhopper -DartifactId=forked-graphhopper-reader-osm -Dpackaging=jar -Dversion=0.11.0-SNAPSHOT
mvn deploy:deploy-file -Durl=file://$(pwd)/../maven_repository/ -Dfile=core/target/graphhopper-core-0.11-SNAPSHOT.jar -DgroupId=com.graphhopper -DartifactId=graphhopper-core -Dpackaging=jar -Dversion=0.11.0-SNAPSHOT
mvn deploy:deploy-file -Durl=file://$(pwd)/../maven_repository/ -Dfile=web/target/graphhopper-web-0.11-SNAPSHOT.jar -DgroupId=com.graphhopper -DartifactId=graphhopper-web -Dpackaging=jar -Dversion=0.11.0-SNAPSHOT
mvn deploy:deploy-file -Durl=file://$(pwd)/../maven_repository/ -Dfile=reader-osm/target/graphhopper-reader-osm-0.11-SNAPSHOT.jar -DgroupId=com.graphhopper -DartifactId=graphhopper-reader-osm -Dpackaging=jar -Dversion=0.11.0-SNAPSHOT
cd $BASEDIR
mvn compile assembly:single -U
