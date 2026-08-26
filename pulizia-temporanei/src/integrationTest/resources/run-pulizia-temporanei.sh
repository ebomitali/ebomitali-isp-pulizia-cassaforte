#!/bin/bash
# Run groovy script in current working directory (CliRunner sets this)
# RunPuliziaTemporanei.groovy and FullPuliziaTemporanei.groovy should be in CWD
# SLF4J jars deployed to workDir/lib
groovy -Dorg.slf4j.simpleLogger.defaultLogLevel=debug -cp lib/slf4j-api-2.0.13.jar:lib/slf4j-simple-2.0.13.jar RunPuliziaTemporanei.groovy "$@" || exit $?
