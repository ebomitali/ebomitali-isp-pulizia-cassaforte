LISTA="${1}"
ENV="${2}"
BUILD_GROUP="${3}"

if [ -z "$LISTA" ] || [ -z "$ENV" ] || [ -z "$BUILD_GROUP" ]; then
  echo "Error: LISTA, ENV, and BUILD_GROUP parameters must not be null"
  exit 1
fi

groovy -cp "$GROOVY_CLASSPATH" RunPuliziaCassaforte.groovy "$LISTA" "$ENV" "$BUILD_GROUP" || result=$?
