SOURCE="${1}"
ENV="${2}"
BUILD_GROUP="${3}"

if [ -z "$SOURCE" ] || [ -z "$ENV" ] || [ -z "$BUILD_GROUP" ]; then
  echo "Error: SOURCE, ENV, and BUILD_GROUP parameters must not be null"
  exit 1
fi

echo "Launching: groovy -cp \"$GROOVY_CLASSPATH\" PuliziaPostBuild.groovy \"$SOURCE\" \"$ENV\" \"$BUILD_GROUP\""
groovy -cp "$GROOVY_CLASSPATH" PuliziaPostBuild.groovy "$SOURCE" "$ENV" "$BUILD_GROUP" || result=$?
echo "PuliziaPostBuild.groovy returned: $result"
