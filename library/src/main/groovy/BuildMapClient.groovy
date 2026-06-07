import groovy.util.logging.Slf4j

/**
 * Trait (interface) for querying the DBB build map to resolve generated output objects.
 *
 * <p>In the cassaforte deletion flow, when a {@link DeletionRule} has {@code useBuildMap = true},
 * the generated member name (which may differ from the source name) must be looked up before
 * the PDS entry can be deleted.  This trait decouples that lookup from the deletion logic.
 *
 * <p>Three implementations:
 * <ul>
 *   <li>{@link JsonBuildMapClient}  — reads a pre-captured JSON fixture; no IBM deps; used in unit tests and local dev.</li>
 *   <li>{@code Db2BuildMapClient}   — queries the live DBB DB2 metadata store on USS; lazy DB2 connection.</li>
 *   <li>{@code DbbBuildMapClient}   — pulls {@code BUILD_GROUP} from a running DBB {@code BuildContext}; no extra DB2 connection.</li>
 * </ul>
 *
 * @see DeletionRule#useBuildMap
 * @see DeleteCassaforteLogic
 */
@Slf4j
abstract class BuildMapClient {
    String buildGroupName
    abstract List<Map<String, String>> getGeneratedObjects(String sourcePath)
}
