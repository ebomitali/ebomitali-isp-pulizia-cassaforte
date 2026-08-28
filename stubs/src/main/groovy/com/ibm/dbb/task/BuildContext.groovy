package com.ibm.dbb.task

import org.apache.commons.cli.CommandLine

/**
 * ============================================================================
 *  STUB CLASS -- compileOnly, off-host use only.
 *  Real implementation is provided by dbb.jar ($DBB_HOME/lib) on z/OS USS.
 *  DO NOT ship, execute, or package this class -- it exists only so that
 *  Groovy task scripts referencing DBB API classes can be syntax/type
 *  checked and compiled (groovyc / IDE) outside of USS (Strategy D).
 * ============================================================================
 *
 * com.ibm.dbb.task.BuildContext
 *
 * Build-scoped variable store, shared across the entire zBuilder execution
 * (as opposed to TaskVariables, which is per-file/per-task scoped). Made
 * available to Groovy TaskScript-based scripts as `context`, and to Java
 * tasks (AbstractTask subclasses) as the `context` member set by the
 * required (BuildContext, TaskVariables) constructor.
 *
 * Methods below marked "confirmed" are directly evidenced in the DBB 3.0.3
 * tutorials (CLILogEncoding / GitUtilities examples). Methods marked
 * "typical / verify" follow the same naming convention documented for
 * TaskVariables but were not directly quoted in the excerpts reviewed --
 * confirm exact signatures against the real dbb.jar Javadoc / API guide
 * before depending on them for anything host-critical.
 *
 * get(String) is confirmed in active use by
 * library/src/main/groovy/com/intesasanpaolo/bes/pc/DbbBuildMapClient.groovy
 * (context.get('BUILD_GROUP')); getBuildFile()/getWorkingDirectory() are
 * confirmed in active use by front-end/scripts/groovy/PuliziaPostBuild.groovy
 * and PuliziaPostBuildLocal.groovy (context.getBuildFile(),
 * context.getWorkingDirectory()) -- kept here merged with the getXxxVariable
 * family below, which previously lived in a second, colliding class
 * definition under src/main/java.
 */
class BuildContext {

    // ---- confirmed ---------------------------------------------------
    /**
     * Returns the org.apache.commons.cli.CommandLine object registered
     * under the given key (typically TaskConstants.COMMAND_LINE), placed
     * into the context by the zBuilder for the active lifecycle's CLI
     * options.
     */
    CommandLine getCommandLine(String key) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    /**
     * Sets/overwrites a build-scoped context variable, e.g.:
     *   context.setVariable("CLI_LOG_ENCODING", encoding)
     */
    void setVariable(String name, Object value) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    /**
     * Returns the raw Object value of a build-scoped context variable, or
     * null if not set. Confirmed in active use as context.get('BUILD_GROUP').
     */
    Object get(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    /**
     * Returns the absolute path of the source file currently being built,
     * e.g.:
     *   def sourcePath = context.getBuildFile()
     */
    String getBuildFile() {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    /**
     * Returns the build's working directory, e.g.:
     *   new File(context.getWorkingDirectory(), 'build-data/rules.csv')
     */
    File getWorkingDirectory() {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    // ---- typical / verify against real dbb.jar before relying on it --
    Object getVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    String getStringVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    Integer getIntVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    Boolean getBooleanVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    boolean hasVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }
}
