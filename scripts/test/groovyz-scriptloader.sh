#!/bin/sh
# Use the com.ibm.dbb.groovy.ScriptLoader to verify script resolution and caching is working correctly
# even when using complex pattern (multiple classes, dependencies, etc) 
# and that the loaded script maintains scope and can be used
groovyz -Dorg.slf4j.simpleLogger -Dorg.slf4j.simpleLogger.defaultLogLevel=debug groovyz-scriptloader.groovy