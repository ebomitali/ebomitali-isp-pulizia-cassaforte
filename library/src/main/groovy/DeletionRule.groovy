/**
 * Immutable value object representing one row from the deletion-rules CSV file.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code typePattern}     — member-type glob matched against the source file type
 *                                 ({@code %} = exactly one char, {@code *} = zero or more).</li>
 *   <li>{@code libraryTemplate} — parametric PDS name with {@code ${C1STAGE}} / {@code ${C1SYSTEM}}
 *                                 placeholders resolved at runtime by {@link LibraryNameResolver}.</li>
 *   <li>{@code useBuildMap}     — when {@code true}, the generated object name is resolved via the
 *                                 DBB build map instead of being derived directly from the source name.</li>
 * </ul>
 *
 * @see DeletionRulesLoader
 * @see PatternMatcher
 * @see LibraryNameResolver
 */
@groovy.util.logging.Slf4j
@groovy.transform.Immutable
class DeletionRule {
    String typePattern
    String libraryTemplate
    boolean useBuildMap
}
