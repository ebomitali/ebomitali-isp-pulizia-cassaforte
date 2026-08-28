package com.ibm.dbb.groovy

/**
 * ============================================================================
 *  STUB CLASS -- compileOnly, off-host use only.
 *  Real implementation is provided by dbb.jar ($DBB_HOME/lib) on z/OS USS.
 *  DO NOT ship, execute, or package this class -- it exists only so that
 *  Groovy task scripts referencing DBB API classes can be syntax/type
 *  checked and compiled (groovyc / IDE) outside of USS (Strategy D).
 * ============================================================================
 *
 * com.ibm.dbb.groovy.AbstractLoader
 *
 * Every Groovy script executed by the zBuilder as a task (either directly
 * via the `script:`/`class:` task attributes, or auto-discovered by name
 * in $DBB_BUILD/groovy) is required, at minimum, to extend this class.
 * In practice this is done indirectly, by declaring one of its subclasses
 * (TaskScript, ScriptLoader, ...) as the script's @BaseScript, e.g.:
 *
 *   @groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript
 *
 * AbstractLoader itself extends groovy.lang.Script, which is what allows
 * the Groovy compiler to treat a plain .groovy source file as an
 * executable "script class" (top-level statements become the body of the
 * generated run() method).
 */
abstract class AbstractLoader extends Script {

    // No additional members are required by the public contract documented
    // in the DBB 3.0.3 guide for AbstractLoader itself; run() is supplied
    // by the Groovy compiler for the concrete generated script class, so
    // it is intentionally left unimplemented here.
}
