#!/bin/bash
# Run groovy script in current working directory (CliRunner sets this)
# RunPuliziaTemporanei.groovy and FullPuliziaTemporanei.groovy should be in CWD
# SLF4J jars deployed to workDir/lib
echo "Run groovy -Dorg.slf4j.simpleLogger.defaultLogLevel=info -cp lib/* RunPuliziaTemporanei.groovy $@"
# echo "Current working directory: $(pwd)"
# echo "Working directory contents: $(ls -la)"
# echo "Working directory contents: $(ls -la lib)"
groovy -Dorg.slf4j.simpleLogger.defaultLogLevel=info -cp "lib/*" RunPuliziaTemporanei.groovy "$@" || exit $?
