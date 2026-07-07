package com.ibm.dbb.task

/**
 * ============================================================================
 *  STUB CLASS -- compileOnly, off-host use only.
 *  Real implementation is provided by dbb.jar ($DBB_HOME/lib) on z/OS USS.
 *  DO NOT ship, execute, or package this class.
 * ============================================================================
 *
 * com.ibm.dbb.task.TaskVariables
 *
 * File/task-scoped variable store. For a top-level `task:`, this holds the
 * task's own YAML `variables:` (plus dbb-app.yaml overrides). For a
 * `type: task` step nested inside a Language task, it IS the enclosing
 * Language task's TaskVariables instance (steps of type task do not get
 * their own variable scope).
 *
 * Made available to Groovy TaskScript-based scripts as `config`, and to
 * Java tasks (AbstractTask subclasses) as the `config` member set by the
 * required (BuildContext, TaskVariables) constructor.
 *
 * Methods below marked "confirmed" are directly evidenced in the DBB 3.0.3
 * tutorials (CLILogEncoding / GitUtilities examples). Methods marked
 * "typical / verify" follow the documented naming convention (e.g. the
 * getIntVariable pattern used for step return codes,
 * config.getIntVariable("<stepName>")) but were not directly quoted for
 * every type -- confirm exact signatures against the real dbb.jar Javadoc
 * before depending on them for anything host-critical.
 */
class TaskVariables {

    // ---- confirmed -----------------------------------------------------
    /**
     * Returns the value of a variable as a String, e.g.:
     *   config.getStringVariable(TaskConstants.FILE_PATH)
     */
    String getStringVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    /**
     * Sets/overwrites a variable, e.g.:
     *   config.setVariable("GIT_HASH", hash)
     */
    void setVariable(String name, Object value) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    // ---- typical / verify against real dbb.jar before relying on it ----
    /**
     * Returns the raw Object value of a variable, or null if not set.
     */
    Object getVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    /**
     * Returns the value of a variable as an Integer. Used, among other
     * things, to read a prior step's return code:
     *   config.getIntVariable("<stepName>")
     */
    Integer getIntVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    Boolean getBooleanVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    List<String> getListVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    boolean hasVariable(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }

    /**
     * Returns the raw Object value of a task-scoped variable, or null if not
     * set. Mirrors BuildContext.get(String) — added so TaskScript-based
     * scripts can use the same .get("KEY") convention for both config and
     * context.
     */
    Object get(String name) {
        throw new UnsupportedOperationException("stub - not for execution")
    }
}
