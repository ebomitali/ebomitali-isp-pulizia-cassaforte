package com.intesasanpaolo.bes.pt
import groovy.util.logging.Slf4j
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Local testing implementation of DatasetService.
 * Simulates z/OS datasets as directories under a base directory.
 *
 * <p>Path mapping: //MY.TEMP.ABC → <baseDir>/MY/TEMP/ABC/
 */
@Slf4j
class MacosDatasetService implements DatasetService {
    String baseDir

    MacosDatasetService(String baseDir) {
        this.baseDir = baseDir
    }

    boolean exists(String dsn) {
        Path p = resolvePath(dsn)
        Files.isDirectory(p)
    }

    void deleteDataset(String dsn) {
        Path p = resolvePath(dsn)
        if (!Files.exists(p)) {
            log.warn("Dataset does not exist: {}", dsn)
            return
        }
        Files.walk(p)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.delete(it) }
        log.debug("Deleted dataset: {}", dsn)
    }

    List<String> listDatasets(String dsnPattern) {
        // Convert DSN pattern to filesystem pattern
        // MY.TEMP.* → MY/TEMP/*
        String pathPattern = dsnPattern.replace('.', '/')

        // Parse the pattern to find the base directory and wildcard part
        List<String> parts = pathPattern.tokenize('/')
        Path basePath = Paths.get(baseDir)

        // Find the first wildcard and build the search directory
        int wildcardIdx = parts.findIndexOf { it.contains('*') || it.contains('%') }
        if (wildcardIdx < 0) {
            // No wildcard, just check if exact dataset exists
            Path exactPath = resolvePath(dsnPattern)
            return Files.exists(exactPath) ? [dsnPattern] : []
        }

        // Build path up to the wildcard
        Path searchDir = basePath
        for (int i = 0; i < wildcardIdx; i++) {
            searchDir = searchDir.resolve(parts[i])
        }

        if (!Files.isDirectory(searchDir)) {
            log.debug("Search directory does not exist: {}", searchDir)
            return []
        }

        // Get remaining pattern parts
        List<String> remainingParts = parts.drop(wildcardIdx)

        // List matching directories
        List<String> results = []
        try {
            Files.list(searchDir).each { path ->
                if (Files.isDirectory(path)) {
                    String dirName = path.fileName.toString()
                    if (matchesPattern(dirName, remainingParts[0])) {
                        // If there are more parts, recursively search
                        if (remainingParts.size() > 1) {
                            String subPattern = remainingParts.drop(1).join('.')
                            String baseDsn = parts.take(wildcardIdx).join('.') + '.' + dirName
                            results.addAll(listDatasetsRecursive(path, subPattern, baseDsn))
                        } else {
                            // Convert back to DSN format
                            String dsn = parts.take(wildcardIdx).join('.') + '.' + dirName
                            results.add(dsn)
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error listing datasets: {}", e.message, e)
        }

        results
    }

    private List<String> listDatasetsRecursive(Path currentPath, String remainingPattern, String currentDsn) {
        List<String> results = []
        List<String> patternParts = remainingPattern.tokenize('/')

        if (patternParts.isEmpty()) {
            results.add(currentDsn)
            return results
        }

        try {
            Files.list(currentPath).each { path ->
                if (Files.isDirectory(path)) {
                    String dirName = path.fileName.toString()
                    if (matchesPattern(dirName, patternParts[0])) {
                        if (patternParts.size() > 1) {
                            String subPattern = patternParts.drop(1).join('/')
                            results.addAll(listDatasetsRecursive(path, subPattern, currentDsn + '.' + dirName))
                        } else {
                            results.add(currentDsn + '.' + dirName)
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error in recursive listing: {}", e.message, e)
        }

        results
    }

    private boolean matchesPattern(String name, String pattern) {
        // Convert z/OS pattern to regex
        // % = single character, * = zero or more characters
        String regex = pattern
            .replace('.', '\\.')
            .replace('%', '.')
            .replace('*', '.*')
        name.matches(regex)
    }

    private Path resolvePath(String dsn) {
        // Convert DSN format to filesystem path
        // //MY.TEMP.ABC → <baseDir>/MY/TEMP/ABC
        String pathStr = dsn.replaceAll('^//', '').replace('.', '/')
        Paths.get(baseDir, pathStr)
    }
}
