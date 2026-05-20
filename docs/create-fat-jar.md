# Create fat jar
## Compilazione dei moduli sorgente in JAR
groovyc \
  -cp "$DBB_HOME/lib/*" \
  -d ./classes \
  LibraryHelper.groovy StageResolver.groovy MetadataHelper.groovy

# Estrai la jar originaria
cd classes
jar xf ../libs/pulizia-cassaforte.jar
cd ..

jar cf pulizia-cassaforte.jar -C classes .
mv pulizia-cassaforte.jar ../libs/pulizia-cassaforte.jar


## Invocazione del main con la JAR nel classpath
groovyz -classpath ./pulizia-cassaforte.jar PuliziaCassaforta.groovy ....