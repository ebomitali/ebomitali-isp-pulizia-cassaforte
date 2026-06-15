package com.ibm.jzos;

/**
 * compileOnly STUB of {@code com.ibm.jzos.ZFile} — only the members JzosFileService uses.
 *
 * Not for runtime: on z/OS the real JZOS ZFile must be on the classpath. Bodies throw so an
 * accidental runtime invocation of the stub fails loudly instead of returning a wrong value.
 */
class ZFile {

    ZFile(String filename, String options) throws ZFileException {
        throw new UnsupportedOperationException("JZOS ZFile stub");
    }

    static boolean exists(String filename) {
        throw new UnsupportedOperationException("JZOS ZFile stub");
    }

    static boolean remove(String filename) throws ZFileException {
        throw new UnsupportedOperationException("JZOS ZFile stub");
    }

    // Real signature throws com.ibm.jzos.RcException on a non-zero RC. RcException is not
    // referenced by JzosFileService (the free() path catches it via Exception), so no throws
    // clause and no RcException stub are needed here to compile.
    static String bpxwdyn(String command) {
        throw new UnsupportedOperationException("JZOS ZFile stub");
    }

    int read(byte[] buf) throws ZFileException {
        throw new UnsupportedOperationException("JZOS ZFile stub");
    }

    void write(byte[] buf, int offset, int len) throws ZFileException {
        throw new UnsupportedOperationException("JZOS ZFile stub");
    }

    void close() throws ZFileException {
        throw new UnsupportedOperationException("JZOS ZFile stub");
    }
}