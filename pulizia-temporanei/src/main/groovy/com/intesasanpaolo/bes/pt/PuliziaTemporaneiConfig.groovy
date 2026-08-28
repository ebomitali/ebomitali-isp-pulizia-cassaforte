package com.intesasanpaolo.bes.pt

/**
 * Configuration for PuliziaTemporanei dataset deletion.
 * Simpler than PuliziaCassaforteConfig: no rules, no stagemap, no build map.
 * Only manages file operations type and filesystem base directory.
 */
class PuliziaTemporaneiConfig {
    /**
     * Type of file operations to use: 'zos', 'uss', or 'macos'.
     * - 'zos': JzosDatasetService (production on z/OS)
     * - 'uss': UssDatasetService (USS filesystem simulation)
     * - 'macos': MacosDatasetService (local testing)
     */
    String fileOpsType = 'zos'

    /**
     * Base directory for filesystem simulations (uss, macos).
     * Required when fileOpsType is 'uss' or 'macos'.
     * Examples: /tmp/zos-sim, /var/tmp/datasets
     */
    String uxBasedir = null

    /**
     * Parse configuration from Properties.
     *
     * @param props Properties object with optional keys:
     *              - fileOpsType: 'zos' (default), 'uss', or 'macos'
     *              - uxBasedir: base directory for filesystem ops
     * @return new PuliziaTemporaneiConfig instance
     */
    static PuliziaTemporaneiConfig from(Properties props) {
        new PuliziaTemporaneiConfig(
            fileOpsType: props.getProperty('fileOpsType', 'zos'),
            uxBasedir: props.getProperty('uxBasedir')
        )
    }

    /**
     * Validate configuration.
     * Throws IllegalArgumentException if configuration is invalid.
     */
    void validate() {
        if (fileOpsType in ['uss', 'macos'] && !uxBasedir?.trim()) {
            throw new IllegalArgumentException(
                "uxBasedir must be defined when fileOpsType is set to '${fileOpsType}'"
            )
        }
        if (fileOpsType && !(fileOpsType in ['zos', 'uss', 'macos'])) {
            throw new IllegalArgumentException(
                "Unknown fileOpsType: '${fileOpsType}' (expected: zos, uss, or macos)"
            )
        }
    }

    @Override
    String toString() {
        "PuliziaTemporaneiConfig(fileOpsType=$fileOpsType, uxBasedir=$uxBasedir)"
    }
}
