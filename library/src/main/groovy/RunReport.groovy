import groovy.util.logging.Slf4j

/**
 * Accumulates per-file results for a single {@link PuliziaCassaforteImpl#run} execution
 * and serializes them to a JSON report file.
 *
 * <p>Call {@link #addEntry} once per C-action source file, then {@link #writeTo} at the end
 * of the run to persist the report.
 */
@Slf4j
class RunReport {

    private final String listFile
    private final String environment
    private final String buildGroup
    private final List   entries = []

    RunReport(String listFile, String environment, String buildGroup) {
        this.listFile    = listFile
        this.environment = environment
        this.buildGroup  = buildGroup
    }

    void addEntry(String sourcePath, String fileType, List<MatchResult> matches) {
        entries << [
            sourcePath: sourcePath,
            fileType  : fileType,
            matches   : matches.collect { mr -> [
                rule: [
                    typePattern    : mr.rule.typePattern,
                    libraryTemplate: mr.rule.libraryTemplate,
                    useBuildMap    : mr.rule.useBuildMap
                ],
                library       : mr.library,
                deletedElement: mr.deletedElement
            ]}
        ]
    }

    void writeTo(String path) {
        def payload = [
            listFile   : listFile,
            environment: environment,
            buildGroup : buildGroup,
            timestamp  : java.time.Instant.now().toString(),
            files      : entries
        ]
        new File(path).text = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(payload))
        log.info("JSON report written to '{}'", path)
    }
}
