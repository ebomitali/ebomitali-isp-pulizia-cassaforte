package com.ibm.jzos;

/** Stub — replaces IBM JZOS jar for local compilation. */
public class ZFile {
    public ZFile(String spec, String options) throws ZFileException {}

    public int read(byte[] buf) throws ZFileException { return -1; }

    public void write(byte[] buf, int off, int len) throws ZFileException {}

    public void close() throws ZFileException {}

    public static boolean dsExists(String mvsName) throws ZFileException { return false; }

    public static void remove(String spec) throws ZFileException {}

    public static String[] listMembers(String mvsName) throws ZFileException { return new String[0]; }
}
