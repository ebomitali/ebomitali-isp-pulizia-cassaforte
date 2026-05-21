/**
 * Loads the ISP stage-map CSV into a lookup map.
 *
 * <p>CSV format (semicolon-delimited, keys and values wrapped in double-quotes,
 * with optional leading whitespace per line):
 * <pre>
 *   "01|ATO";"X2A"
 * </pre>
 * Key format: {@code PATH_LO|BUILD_ENV} (e.g. {@code "01|ATO"}).
 * Value: C1STAGE code (e.g. {@code "X2A"}).
 */
class StageMapLoader {

    Map<String, String> load(String csvPath) {
        new File(csvPath).readLines()
            .findAll { it.trim() }
            .collectEntries { line ->
                def parts = line.trim().split(';')
                if (parts.size() < 2)
                    throw new IllegalArgumentException("Malformed stage-map row: '$line'")
                def key   = parts[0].trim().replace('"', '')
                def value = parts[1].trim().replace('"', '')
                [key, value]
            }
    }
}
