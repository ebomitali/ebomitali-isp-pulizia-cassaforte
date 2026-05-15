import java.nio.file.*

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
