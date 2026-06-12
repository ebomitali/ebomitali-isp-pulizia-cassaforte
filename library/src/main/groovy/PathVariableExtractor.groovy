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

    // Jobz (fileType 'STWSNCS','STWSJGO','STWSJGM') paths have no application segment; 
    // layer operativo is always '01' and C1SYSTEM is not applicable.
    // Jobz extensions are set as a default in PuliziaCassaforteConfig 
    // and can be overridden via properties or test code.
    Map<String, String> extractJobz(String buildEnv, Map<String, String> stageMap, String hlq, String fileType) {
        def key = "01|${buildEnv}"
        // if fileType is STWSNCS, STWSJGO, or STWSJGM and buildEnv is PR then set key to fileType|PR
        if (fileType in ['STWSNCS', 'STWSJGO'] && buildEnv == 'PR') {
            key = "${fileType}|${buildEnv}"
            log.trace("extractJobz: special case for fileType='{}' and buildEnv='{}', using key='{}'", fileType, buildEnv, key)
        }
        def c1stagep = stageMap[key]
        if (!c1stagep)
            throw new IllegalArgumentException(
                "No stage-map entry for '${key}' (jobz path, env='${buildEnv}')"
            )
        def result = [C1STAGE: c1stagep, C1STAGEP: c1stagep, C1SYSTEM: '', HLQ: hlq ?: '']
        log.debug("extractJobz (special case): env='{}' C1STAGE='{}' C1STAGEP='{}' HLQ='{}'", 
                    buildEnv, c1stagep, c1stagep, hlq)
        return result
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
