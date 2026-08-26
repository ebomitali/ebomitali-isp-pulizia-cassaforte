#!/bin/bash
echo "Running PuliziaTemporanei with arguments: $@"
# Run groovy script in current working directory (CliRunner sets this)
# RunPuliziaTemporanei.groovy and FullPuliziaTemporanei.groovy should be in CWD
# GROOVY_CLASSPATH must be set by the caller to include the jar with PuliziaTemporaneiImpl
groovy -cp "$GROOVY_CLASSPATH" RunPuliziaTemporanei.groovy "$@" || exit $?
