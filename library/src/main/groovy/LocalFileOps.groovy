import java.nio.file.*

/**
 * Local-filesystem adapter of the {@link ZosFileOps} trait used during unit testing.
 *
 * <p>Simulates a z/OS PDS environment by mapping z/OS-style dataset paths onto ordinary
 * directories under a configurable {@code baseDir} (default: {@code /tmp/zos-sim/}):
 * <pre>
 *   //DATASET.NAME(MEMBER)  →  &lt;baseDir&gt;/DATASET.NAME/MEMBER   (PDS member = file)
 *   //DATASET.NAME          →  &lt;baseDir&gt;/DATASET.NAME           (PDS = directory)
 *   /path/to/file           →  /path/to/file                     (USS path, passed through)
 * </pre>
 *
 * <p>Uses {@link java.nio.file.Files} for all I/O — no IBM/DBB dependencies.
 * Tests inject a JUnit 5 {@code @TempDir} as {@code baseDir} for full isolation.
 *
 * @see ZosFileOps
 * @see ZosFileOpsUSS
 */
class LocalFileOps implements ZosFileOps {
    final String baseDir

    LocalFileOps(String baseDir = '/tmp/zos-sim') {
        this.baseDir = baseDir
    }

    private Path resolve(String zosPath) {
        if (zosPath.startsWith('//')) {
            def inner = zosPath.substring(2)
            def m = (inner =~ /^(.+?)\((.+?)\)$/)
            if (m.matches()) return Paths.get(baseDir, m.group(1), m.group(2))
            return Paths.get(baseDir, inner)
        }
        Paths.get(zosPath)
    }

    boolean exists(String path) { Files.exists(resolve(path)) }

    void delete(String path) { Files.deleteIfExists(resolve(path)) }

    void copy(String src, String dst) {
        def dstPath = resolve(dst)
        Files.createDirectories(dstPath.parent)
        def srcPath = resolve(src)
        if (Files.exists(srcPath)) {
            Files.copy(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING)
        } else {
            Files.createFile(dstPath)
        }
    }

    List<String> list(String container) {
        def dir = resolve(container)
        if (!Files.isDirectory(dir)) return []
        dir.toFile().list()?.toList() ?: []
    }
}
