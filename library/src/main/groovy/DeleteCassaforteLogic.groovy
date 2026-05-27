import groovy.util.logging.Slf4j

/**
 * Core business logic for deleting members from cassaforte (staging) PDS libraries.
 *
 * <p>For each source file to process, the caller provides its path, resolved file type,
 * environment stage code, system identifier, and DBB build group.  This class:
 * <ol>
 *   <li>Filters the loaded {@link DeletionRule} list by matching the file type against
 *       each rule's {@code typePattern} via {@link PatternMatcher}.</li>
 *   <li>Resolves the target PDS name from the rule's {@code libraryTemplate} using
 *       {@link LibraryNameResolver}.</li>
 *   <li>Determines the PDS member name — either directly from the source filename, or
 *       by querying the {@link BuildMapClient} when {@code useBuildMap = true}.</li>
 *   <li>Calls {@link ZosFileOps#delete} on each matching member that exists.</li>
 * </ol>
 *
 * <p>This class has zero IBM/DBB imports; all environment interaction is injected
 * via the {@link ZosFileOps} and {@link BuildMapClient} traits.
 *
 * @see DeletionRule
 * @see SfilamentoLogic
 * @see PrevEnvCleanLogic
 */
@Slf4j
class DeleteCassaforteLogic {
    ZosFileOps          ops
    List<DeletionRule>  rules
    BuildMapClient      buildMap
    PatternMatcher      matcher  = new PatternMatcher()
    LibraryNameResolver resolver = new LibraryNameResolver()

    // vars map must contain C1STAGE, C1SYSTEM, HLQ — resolved by caller per source file.
    // Returns number of delete operations performed.
    int execute(String sourcePath, String fileType, Map<String,String> vars, String buildGroup) {
        def member   = memberName(sourcePath)
        def matching = rules.findAll { matcher.matches(it.typePattern, fileType) }
        log.debug("execute: sourcePath='{}' fileType='{}' buildGroup='{}' matchingRules={}",
                  sourcePath, fileType, buildGroup, matching.size())
        int count    = 0

        matching.each { rule ->
            def lib = resolver.resolve(rule.libraryTemplate, vars)
            if (rule.useBuildMap) {
                buildMap.getGeneratedObjects(sourcePath, buildGroup).each { obj ->
                    def zp = "//${lib}(${obj.member})"
                    if (ops.exists(zp)) {
                        log.info("Deleting (build-map): {}", zp)
                        ops.delete(zp)
                        count++
                    }
                }
            } else {
                def zp = "//${lib}(${member})"
                if (ops.exists(zp)) {
                    log.info("Deleting: {}", zp)
                    ops.delete(zp)
                    count++
                }
            }
        }
        log.debug("execute: {} deletion(s) performed for '{}'", count, sourcePath)
        count
    }

    static String memberName(String sourcePath) {
        def filename = sourcePath.tokenize('/').last()
        def name = filename.contains('.') ? filename.take(filename.lastIndexOf('.')) : filename
        name.toUpperCase()
    }
}
