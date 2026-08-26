#!/bin/bash

# Run groovy script in current working directory (CliRunner sets this)
# RunPuliziaTemporanei.groovy and FullPuliziaTemporanei.groovy should be in CWD
groovy RunPuliziaTemporanei.groovy "$@" || exit $?
