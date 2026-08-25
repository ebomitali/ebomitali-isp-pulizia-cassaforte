package com.intesasanpaolo.bes.pt
import groovy.util.logging.Slf4j

/**
 * Core business logic for deleting z/OS datasets matching a partial DSN pattern.
 *
 * <p>For a given DSN pattern, this class:
 * <ol>
 *   <li>Queries the DatasetService to list all datasets matching the pattern.</li>
 *   <li>Attempts to delete each matching dataset.</li>
 *   <li>Logs errors for individual failures, but continues with remaining datasets.</li>
 *   <li>Returns count of successfully deleted datasets.</li>
 * </ol>
 *
 * <p>This class has zero IBM/DBB imports; all environment interaction is injected
 * via the DatasetService trait.
 */
@Slf4j
class DeleteTemporaneiLogic {
    DatasetService ops

    /**
     * Execute dataset deletion for a given pattern.
     *
     * @param dsnPattern DSN pattern with z/OS wildcards (% = 1 char, * = 0+ chars)
     *                   Examples: MY.TEMP.*, MY.*.DATASET
     * @return count of successfully deleted datasets
     * @throws IllegalArgumentException if dsnPattern is null or empty
     */
    int execute(String dsnPattern) {
        if (!dsnPattern?.trim()) {
            throw new IllegalArgumentException('dsnPattern argument is required')
        }

        String trimmedPattern = dsnPattern.trim()
        log.info("Listing datasets matching pattern: '{}'", trimmedPattern)

        List<String> matching = ops.listDatasets(trimmedPattern)
        log.info("Found {} dataset(s) matching pattern '{}'", matching.size(), trimmedPattern)

        int count = 0
        matching.each { String dsn ->
            try {
                log.info("Deleting dataset: {}", dsn)
                ops.deleteDataset(dsn)
                count++
            } catch (Exception e) {
                log.error("ERROR deleting '{}': {}", dsn, e.message, e)
            }
        }

        log.info("DeleteTemporaneiLogic: {} deletion(s) completed for pattern '{}'", count, trimmedPattern)
        count
    }
}
