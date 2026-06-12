package com.ibm.jzos;

import java.util.Iterator;
import java.util.Collections;

/**
 * Stub for IBM JZOS PdsDirectory - reads PDS member directory.
 * Provides iteration over member information for a partitioned dataset.
 *
 * Real implementation available in JZOS runtime on z/OS.
 */
public class PdsDirectory implements Iterable<PdsDirectory.MemberInfo> {

    private String dataset;

    /**
     * Create a PdsDirectory for the given dataset.
     * @param dataset Dataset path (e.g., "//'MY.PDS'")
     */
    public PdsDirectory(String dataset) {
        this.dataset = dataset;
    }

    /**
     * Close the directory reader.
     */
    public void close() {
        // Stub: no-op
    }

    /**
     * Get iterator over member information.
     * @return Iterator of MemberInfo objects
     */
    @Override
    public Iterator<MemberInfo> iterator() {
        // Stub: return empty iterator
        // Real implementation returns actual members from dataset
        return Collections.emptyIterator();
    }

    /**
     * Member information from PDS directory.
     * Contains metadata about a PDS member.
     */
    public static class MemberInfo {
        private String name;
        private boolean alias;

        public MemberInfo(String name) {
            this.name = name;
            this.alias = false;
        }

        /**
         * Get member name.
         * @return Member name (up to 8 characters)
         */
        public String getName() {
            return name;
        }

        /**
         * Check if member is an alias.
         * @return true if member is an alias
         */
        public boolean isAlias() {
            return alias;
        }
    }
}
