/**
 * Result of one candidate check during {@link DeleteCassaforteLogic#execute}.
 *
 * <p>One instance is produced for each (rule, member) pair evaluated:
 * a rule that matched the file type and yielded a library + member name.
 * {@code deletedElement} is the full PDS element path when the member existed
 * and was deleted; {@code null} when the member was absent (no-op).
 */
@groovy.transform.Immutable
class MatchResult {
    DeletionRule rule
    String       library
    String       deletedElement  // null if member did not exist
}
