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
class LibraryNameResolver {
    String resolve(String template, String stage, String system) {
        template
            .replace('${C1STAGE}', stage  ?: '')
            .replace('${C1SYSTEM}', system ?: '')
    }

    // Derives TOCOLB library from resolved cassaforte library.
    // 4th qualifier '@@@@' → 'TO@@', 5th qualifier '@@@@@@@@' → 'COLB@@@@'.
    String toTocolbLibrary(String resolvedLibrary) {
        def parts = resolvedLibrary.split('\\.', -1)
        if (parts.size() >= 5) {
            parts[3] = parts[3].replace('@@@@', 'TO@@')
            parts[4] = parts[4].replace('@@@@@@@@', 'COLB@@@@')
        }
        parts.join('.')
    }
}
