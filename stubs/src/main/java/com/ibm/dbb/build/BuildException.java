package com.ibm.dbb.build;

/** Stub — replaces IBM DBB jar for local compilation. */
public class BuildException extends RuntimeException {
    public BuildException(String message) { super(message); }
    public BuildException(String message, Throwable cause) { super(message, cause); }
}
