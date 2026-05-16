/**
 * Reads the deletion-rules CSV file and converts each non-blank, non-comment line
 * into a {@link DeletionRule} instance consumed by the cassaforte cleanup logic.
 *
 * <p>Expected CSV format (semicolon-separated, 3 fields):
 * <pre>
 *   &lt;type-pattern&gt;;&lt;parametric-library&gt;;&lt;flag&gt;
 * </pre>
 * <ul>
 *   <li>{@code type-pattern}   — member-type glob ({@code %} = one char, {@code *} = any chars)</li>
 *   <li>{@code parametric-library} — target PDS template, may contain {@code ${C1STAGE}} / {@code ${C1SYSTEM}}</li>
 *   <li>{@code flag}           — {@code NO} to delete by source name;
 *                                {@code BUILD MAP} to resolve the generated object name via DBB build map</li>
 * </ul>
 *
 * <p>Lines starting with {@code #} are treated as comments and skipped.
 *
 * @see DeletionRule
 * @see scripts/build-data/rules.csv
 */
class DeletionRulesLoader {

    List<DeletionRule> load(String filePath) {
        new File(filePath).readLines()
            .findAll { it.trim() && !it.startsWith('#') }
            .collect { line ->
                def parts = line.split(';', -1)
                if (parts.size() < 3)
                    throw new IllegalArgumentException("Invalid rule (need 3 semicolon-separated fields): '$line'")
                new DeletionRule(
                    typePattern:     parts[0],
                    libraryTemplate: parts[1].trim(),
                    useBuildMap:     parts[2].trim() == 'BUILD MAP'
                )
            }
    }
}
