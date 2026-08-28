package com.intesasanpaolo.bes.pt

/**
 * Trait for dataset-level z/OS operations (entire datasets, not members).
 *
 * <p>Operates on complete datasets. Path format: //DATASET.NAME (no member component).
 *
 * <p>Implementations:
 * <ul>
 *   <li>JzosDatasetService — z/OS JZOS catalog API</li>
 *   <li>UssDatasetService — USS filesystem simulation</li>
 *   <li>MacosDatasetService — local testing (filesystem simulation)</li>
 * </ul>
 */
trait DatasetService {
    /**
     * Check if dataset exists.
     *
     * @param dsn Dataset name (e.g., //MY.DATASET.NAME)
     * @return true if dataset exists, false otherwise
     */
    abstract boolean exists(String dsn)

    /**
     * Delete entire dataset.
     *
     * @param dsn Dataset name to delete
     * @throws IOException or z/OS-specific exception if deletion fails
     */
    abstract void deleteDataset(String dsn)

    /**
     * List all datasets matching a pattern.
     *
     * @param dsnPattern Pattern with z/OS wildcards (% = 1 char, * = 0+ chars)
     *                   Examples: MY.TEMP.*, MY.*.ABC
     * @return List of matching DSN names (without // prefix)
     */
    abstract List<String> listDatasets(String dsnPattern)
}
