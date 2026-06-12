package com.ibm.jzos;

import java.util.Iterator;

/**
 * compileOnly STUB of {@code com.ibm.jzos.PdsDirectory} and its nested {@code MemberInfo} —
 * only the members JzosFileService uses.
 *
 * <p>The real PdsDirectory is iterable over MemberInfo, which is what lets the
 * {@code for (MemberInfo mi : dir)} loop in {@code list()} compile. This stub exists so the
 * code builds off-host (Strategy D) and must NOT be on the runtime classpath, where the real
 * JZOS class shadows it. Bodies throw so any accidental runtime use of the stub fails loudly.
 */
public class PdsDirectory implements Iterable<PdsDirectory.MemberInfo> {

    public PdsDirectory(String filename) throws ZFileException {
        throw new UnsupportedOperationException("JZOS PdsDirectory stub");
    }

    @Override
    public Iterator<MemberInfo> iterator() {
        throw new UnsupportedOperationException("JZOS PdsDirectory stub");
    }

    public void close() {
        throw new UnsupportedOperationException("JZOS PdsDirectory stub");
    }

    /** compileOnly STUB of {@code com.ibm.jzos.PdsDirectory.MemberInfo}. */
    public static class MemberInfo {
        public String getName() {
            throw new UnsupportedOperationException("JZOS MemberInfo stub");
        }
        public boolean isAlias() {
            throw new UnsupportedOperationException("JZOS MemberInfo stub");
        }
    }
}