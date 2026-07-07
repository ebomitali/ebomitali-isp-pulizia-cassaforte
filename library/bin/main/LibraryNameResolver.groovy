import groovy.util.logging.Slf4j

/**
 * Resolves parametric PDS library names by substituting ISP stage and system tokens,
 * and derives TOCOLB library names from cassaforte library names.
 *
 * <h3>Template substitution</h3>
 * <p>Library templates stored in the deletion-rules CSV may contain two placeholders:
 * <ul>
 *   <li>{@code ${C1STAGE}}  — the two-character stage code for the target environment
 *                             (e.g. {@code I1}, {@code O1}, {@code S1}, {@code P1}).</li>
 *   <li>{@code ${C1SYSTEM}} — the application system identifier (e.g. {@code YN}).</li>
 * </ul>
 *
 * <h3>TOCOLB derivation</h3>
 * <p>The sfilamento restore target (TOCOLB library) is computed by transforming qualifiers
 * 4 and 5 (1-based) of the resolved cassaforte library name:
 * <pre>
 *   qualifier 4: replaces the pattern {@code @@@@}     with {@code TO@@}
 *   qualifier 5: replaces the pattern {@code @@@@@@@@} with {@code COLB@@@@}
 * </pre>
 *
 * @see DeletionRule#libraryTemplate
 * @see SfilamentoLogic
 */
@Slf4j
class LibraryNameResolver {
    String resolve(String template, Map<String, String> vars) {
        def result = vars.inject(template) { acc, key, val ->
            acc.replace('${' + key + '}', val ?: '')
        }
        def unresolved = (result =~ /\$\{[^}]+\}/)
        if (unresolved) {
            log.error("Unresolved macro: {} in template: '{}'", unresolved[0], template)
            throw new IllegalStateException(
                "Unresolved macro: ${unresolved[0]} in template: '${template}'"
            )
        }
        log.debug("resolve: '{}' -> '{}'", template, result)
        result
    }

    // Derives TOCOLB library from resolved cassaforte library.
    // 4th qualifier '@@@@' → 'TO@@', 5th qualifier '@@@@@@@@' → 'COLB@@@@'.
    String toTocolbLibrary(String resolvedLibrary) {
        def parts = resolvedLibrary.split('\\.', -1)
        if (parts.size() >= 5) {
            parts[3] = parts[3].replace('@@@@', 'TO@@')
            parts[4] = parts[4].replace('@@@@@@@@', 'COLB@@@@')
        }
        def result = parts.join('.')
        log.debug("toTocolbLibrary: '{}' -> '{}'", resolvedLibrary, result)
        result
    }
}
