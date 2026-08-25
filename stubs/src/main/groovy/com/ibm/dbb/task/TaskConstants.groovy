package com.ibm.dbb.task

/**
 * ============================================================================
 *  STUB CLASS -- compileOnly, off-host use only.
 *  Real implementation is provided by dbb.jar ($DBB_HOME/lib) on z/OS USS.
 *  DO NOT ship, execute, or package this class.
 * ============================================================================
 *
 * com.ibm.dbb.task.TaskConstants
 *
 * Holds the well-known key names used to look up BuildContext/TaskVariables
 * entries and CLI objects (e.g. config.getStringVariable(TaskConstants.FILE_PATH),
 * context.getCommandLine(TaskConstants.COMMAND_LINE)).
 *
 * IMPORTANT: only the two constants below are directly evidenced in the
 * DBB 3.0.3 tutorials / project reference material reviewed. The real
 * class almost certainly defines many more (e.g. for HLQ, LOGS,
 * LOG_ENCODING, SOURCE_LIST, SOURCE_DIRS, TIME_FORMAT -- all of which
 * appear as Build Context outputs in the Start/ScannerInit task reference
 * tables, but NOT confirmed as TaskConstants field names as opposed to
 * plain string literals/YAML variable names). Do not assume additional
 * constants exist here without checking the real dbb.jar Javadoc --
 * add them explicitly, one at a time, as you confirm each one on host,
 * e.g.:
 *
 *   javap -classpath $DBB_HOME/lib/dbb.jar com.ibm.dbb.task.TaskConstants
 */
class TaskConstants {

    /** Key for config.getStringVariable(TaskConstants.FILE_PATH) -> absolute path of the current build file. */
    static final String FILE_PATH = "FILE_PATH"

    /** Key for context.getCommandLine(TaskConstants.COMMAND_LINE) -> org.apache.commons.cli.CommandLine for the active lifecycle. */
    static final String COMMAND_LINE = "COMMAND_LINE"
}
