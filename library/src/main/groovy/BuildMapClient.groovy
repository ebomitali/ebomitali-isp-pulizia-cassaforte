import groovy.util.logging.Slf4j

/**
 * Trait (interface) for querying the DBB build map to resolve generated output objects.
 *
 * <p>In the cassaforte deletion flow, when a {@link DeletionRule} has {@code useBuildMap = true},
 * the generated member name (which may differ from the source name) must be looked up before
 * the PDS entry can be deleted.  This trait decouples that lookup from the deletion logic.
 *
 * <p>Two implementations:
 * <ul>
 *   <li>{@link LocalBuildMapClient} — reads a pre-captured JSON fixture; used in unit tests.</li>
 *   <li>{@code ZosBuildMapClient}   — queries the live DBB build map on USS (not yet implemented).</li>
 * </ul>
 *
 * @see DeletionRule#useBuildMap
 * @see DeleteCassaforteLogic
 */
@Slf4j
trait BuildMapClient {
    abstract List<Map<String, String>> getGeneratedObjects(String sourcePath, String buildGroup)
}
