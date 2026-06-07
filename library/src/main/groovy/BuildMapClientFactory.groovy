import groovy.util.logging.Slf4j

/**
 * Factory for creating {@link BuildMapClient} instances without IBM/DBB compile-time dependencies.
 *
 * <p>Dispatches on {@code buildMapClientType}:
 * <ul>
 *   <li>{@code 'json'} — {@link JsonBuildMapClient}: reads a pre-captured JSON file; no IBM deps;
 *       used for local dev and unit testing.</li>
 *   <li>{@code 'db2'} — {@code Db2BuildMapClient}: connects to the DBB DB2 metadata store on USS;
 *       loaded via reflection to avoid compile-time IBM dependency.</li>
 *   <li>{@code 'dbb'} — {@code DbbBuildMapClient}: reuses the {@code BuildGroup} already resolved
 *       by the {@code MetadataInit} task in a DBB task context; loaded via reflection.</li>
 * </ul>
 *
 * <p>IBM-dep clients ({@code Db2BuildMapClient}, {@code DbbBuildMapClient}) must be on the runtime
 * classpath (from {@code pulizia-cassaforte-zos.jar}) and expose a static
 * {@code create(String buildGroupName, PuliziaCassaforteConfig cfg)} method.
 *
 * @see BuildMapClient
 * @see JsonBuildMapClient
 */
@Slf4j
class BuildMapClientFactory {

    /**
     * Creates a {@link BuildMapClient} for the given type.
     *
     * <p>{@code 'json'} is instantiated directly; {@code 'db2'} and {@code 'dbb'} are loaded
     * via reflection so that this class has zero IBM compile-time dependencies (Strategy D).
     *
     * @param buildMapClientType  {@code 'json'}, {@code 'db2'}, or {@code 'dbb'}.
     * @param buildGroupName      DBB build group name.
     * @param cfg                 Configuration; provides all type-specific parameters.
     * @throws ClassNotFoundException   if the IBM-dep class is not on the runtime classpath.
     * @throws IllegalArgumentException if the type is not recognised.
     */
    static BuildMapClient create(String buildMapClientType,
                                 String buildGroupName,
                                 PuliziaCassaforteConfig cfg) {
        log.debug("Creating BuildMapClient: type='{}' group='{}'", buildMapClientType, buildGroupName)
        switch (buildMapClientType) {
            case 'json':
                return new JsonBuildMapClient(buildGroupName, cfg)
            case 'db2':
                return Class.forName('Db2BuildMapClient')
                            .create(buildGroupName, cfg) as BuildMapClient
            case 'dbb':
                return Class.forName('DbbBuildMapClient')
                            .create(buildGroupName, cfg) as BuildMapClient
            default:
                throw new IllegalArgumentException("Unknown buildMapClientType: '${buildMapClientType}'")
        }
    }
}
