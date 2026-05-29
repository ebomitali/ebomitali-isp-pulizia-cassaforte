import groovy.util.logging.Slf4j

/**
 * Extracts per-file template variables from a source path.
 *
 * <p>Identifies the application segment in the path (the component whose
 * underscore-delimited tokens satisfy {@code tokens[2] =~ /\d+/}), then
 * looks up the C1STAGE in the stage map using {@code PATH_LO|buildEnv}.
 *
 * <p>Returns a map with keys {@code C1STAGE}, {@code C1SYSTEM}, {@code HLQ}.
 *
 * @see StageMapLoader
 * @see LibraryNameResolver
 */
@Slf4j
class PathVariableExtractor {

    // Jobz paths have no application segment; layer operativo is always '01' and C1SYSTEM is not applicable.
    Map<String, String> extractJobz(String buildEnv, Map<String, String> stageMap, String hlq) {
        def key = "01|${buildEnv}"
        def c1stage = stageMap[key]
        if (!c1stage)
            throw new IllegalArgumentException(
                "No stage-map entry for '${key}' (jobz path, env='${buildEnv}')"
            )
        def result = [C1STAGE: c1stage, C1SYSTEM: '', HLQ: hlq ?: '']
        log.debug("extractJobz: env='{}' -> C1STAGE='{}' HLQ='{}'", buildEnv, c1stage, hlq)
        result
    }

    Map<String, String> extract(String sourcePath, String buildEnv,
                                Map<String, String> stageMap, String hlq) {
        def segment = sourcePath.tokenize('/').find { part ->
            def tokens = part.split('_')
            tokens.size() >= 5 && tokens[2] ==~ /\d+/
        }
        if (!segment)
            throw new IllegalArgumentException(
                "No application segment found in source path: '${sourcePath}'"
            )

        def tokens   = segment.split('_')
        def c1system = tokens[1]
        def pathLo   = tokens[2]
        def key      = "${pathLo}|${buildEnv}"
        def c1stage  = stageMap[key]
        if (!c1stage)
            throw new IllegalArgumentException(
                "No stage-map entry for '${key}' (path: '${sourcePath}')"
            )

        def result = [C1STAGE: c1stage, C1SYSTEM: c1system, HLQ: hlq ?: '']
        log.debug("extract: path='{}' env='{}' -> C1STAGE='{}' C1SYSTEM='{}' HLQ='{}'",
                  sourcePath, buildEnv, c1stage, c1system, hlq)
        result
    }
}
