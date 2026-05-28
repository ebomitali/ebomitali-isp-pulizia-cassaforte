#!/bin/sh
# Use the GroovyZ logging configuration to test that logging is working correctly.
# Groovyz disable logging by default unless -Dorg.slf4j.simpleLogger
# Then you may set the common sfl4j simple logger properties to control the logging output.
groovyz -Dorg.slf4j.simpleLogger -Dorg.slf4j.simpleLogger.defaultLogLevel=debug groovyz-logging.groovy