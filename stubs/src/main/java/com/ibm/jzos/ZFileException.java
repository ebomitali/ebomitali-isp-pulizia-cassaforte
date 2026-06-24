package com.ibm.jzos;

import java.io.IOException;

/** Stub — replaces IBM JZOS jar for local compilation. */
public class ZFileException extends IOException {
    ZFileException() { super(); }
    ZFileException(String msg) { super(msg); }
    ZFileException(String msg, Throwable cause) { super(msg, cause); }
}
