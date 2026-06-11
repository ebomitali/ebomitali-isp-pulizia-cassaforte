package com.intesasanpaolo.bes.pc

import groovy.util.logging.Slf4j
import java.nio.file.*

/**
 * USS Unix-filesystem adapter of the {@link FileService} trait.
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
 * Suitable for running on USS HFS/zFS without the IBM JZOS jar.
 *
 * @see FileService
 * @see JzosFileService
 */
@Slf4j
class UssFileService implements FileService {

    final String baseDir

    UssFileService(String baseDir = '/tmp/zos-sim') {
        this.baseDir = baseDir
        log.debug("UssFileService initialized with baseDir: {}", baseDir)
    }

    // Translates a z/OS-style path to a local filesystem path under baseDir.
    // PDS member //HLQ.DS(MEMBER) → <baseDir>/HLQ.DS/MEMBER; plain //HLQ.DS → <baseDir>/HLQ.DS; USS path passed through.
    private Path resolve(String zosPath) {
        if (zosPath.startsWith('//')) {
            def inner = zosPath.substring(2)
            def m = (inner =~ /^(.+?)\((.+?)\)$/) // matches DATASET(MEMBER) pattern
            if (m.matches()) return Paths.get(baseDir, m.group(1), m.group(2))
            return Paths.get(baseDir, inner)
        }
        Paths.get(zosPath)
    }

    boolean exists(String path) {
        def result = Files.exists(resolve(path))
        log.debug("exists({}): {}", path, result)
        result
    }

    void delete(String path) {
        log.debug("delete({})", path)
        Files.deleteIfExists(resolve(path))
    }

    void copy(String src, String dst) {
        log.debug("copy({} -> {})", src, dst)
        def dstPath = resolve(dst)
        Files.createDirectories(dstPath.parent)
        def srcPath = resolve(src)
        if (Files.exists(srcPath)) {
            Files.copy(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING)
        } else {
            Files.createFile(dstPath) // src absent → create empty dst, simulating a z/OS member with no content
        }
    }

    List<String> list(String container) {
        def dir = resolve(container)
        if (!Files.isDirectory(dir)) {
            log.debug("list({}): not a directory", container)
            return []
        }
        def result = dir.toFile().list()?.toList() ?: []
        log.debug("list({}): {} member(s)", container, result.size())
        result
    }

}
