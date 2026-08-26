#!/bin/bash
# Run groovy script in current working directory (CliRunner sets this)
# RunPuliziaTemporanei.groovy and FullPuliziaTemporanei.groovy should be in CWD
# SLF4J jars deployed to workDir/lib
groovy -Dorg.slf4j.simpleLogger.defaultLogLevel=info -cp "lib/*" RunPuliziaTemporanei.groovy "$@" || exit $?
