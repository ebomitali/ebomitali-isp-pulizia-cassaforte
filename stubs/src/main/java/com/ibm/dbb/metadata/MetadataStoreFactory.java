package com.ibm.dbb.metadata;

import java.io.File;
import java.util.Properties;

/** Stub — replaces IBM DBB jar for local compilation. */

public class MetadataStoreFactory {
    /**
     * Creates a DB2 metadata store.
     * @param url the database connection URL
     * @param userId the user ID for authentication
     * @param pwFile the file containing the password
     * @return a MetadataStore instance
     */
    public static MetadataStore createDb2MetadataStore(String url, String userId, File pwFile) {
        return null;
    }

    public static MetadataStore createDb2MetadataStore(String userId, File pwFile, Properties props) {
        return null;
    }
}
