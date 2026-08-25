package com.ibm.dbb.groovy

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.ibm.dbb.task.BuildContext
import com.ibm.dbb.task.TaskVariables

/**
 * ============================================================================
 *  STUB CLASS -- compileOnly, off-host use only.
 *  Real implementation is provided by dbb.jar ($DBB_HOME/lib) on z/OS USS.
 *  DO NOT ship, execute, or package this class.
 * ============================================================================
 *
 * com.ibm.dbb.groovy.TaskScript
 *
 * Base class for DBB Groovy "task" and "type: task" step scripts, as
 * documented in the "Creating a custom task" / "Writing a custom step task"
 * tutorials of the IBM DBB 3.0.3 guide. A script declares it via the
 * @BaseScript AST transformation:
 *
 *   @groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript
 *
 *   import com.ibm.dbb.task.TaskConstants
 *
 *   def cli = context.getCommandLine(TaskConstants.COMMAND_LINE)
 *   log.debug("running")
 *   config.setVariable("MY_VAR", "value")
 *   return 0
 *
 * At runtime, the zBuilder:
 *  - injects an SLF4j logger as `log` (named after the script's class),
 *  - populates the script Binding with the build-scoped `context`
 *    (BuildContext) object,
 *  - populates the script Binding with the file/task-scoped `config`
 *    (TaskVariables) object.
 *
 * The script's generated run() method (i.e. its top-level statements) must
 * return an Integer return code, used by the zBuilder in subsequent step
 * `condition:` expressions.
 *
 * This stub declares `log`, `context`, and `config` as typed properties
 * (rather than relying purely on dynamic Binding resolution) so that
 * off-host compilation/IDE tooling can resolve and type-check them.
 */
abstract class TaskScript extends AbstractLoader {

    /**
     * SLF4j logger injected by the zBuilder.
     * Real logger name/config is driven by simplelogger.properties on host;
     * here it is just wired to a plain SLF4j logger for compile purposes.
     */
    Logger log = LoggerFactory.getLogger(this.class)

    /**
     * Build-scoped context object (shared across the whole build execution).
     * Populated by the zBuilder before the script runs.
     */
    BuildContext context

    /**
     * File/task-scoped variables object.
     * - For a top-level `task:` script, this is the TaskVariables instance
     *   for that task.
     * - For a `type: task` step nested under a Language task, this is the
     *   SAME TaskVariables instance used by the enclosing Language task
     *   (per DBB 3.0.3 docs: "The task cannot contain any variable
     *   definitions and as such, its TaskVariables object is the same as
     *   what's used by the Language task it is executing under.")
     * Populated by the zBuilder before the script runs.
     */
    TaskVariables config
}
