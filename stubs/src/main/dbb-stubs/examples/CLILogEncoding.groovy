// Example: a DBB Groovy "task:" script that compiles off-host against the
// com.ibm.dbb.groovy.TaskScript stub. Adapted from the DBB 3.0.3
// "Creating a custom task" tutorial (CLILogEncoding.groovy).
//
// On z/OS this file must be IBM-1047 (or IBM-1144, per .gitattributes)
// encoded and placed under $DBB_BUILD/groovy/.

@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript

import com.ibm.dbb.task.TaskConstants

// An instance of org.apache.commons.cli.CommandLine placed into the
// context by the zBuilder for the active lifecycle's CLI options.
def cli = context.getCommandLine(TaskConstants.COMMAND_LINE)

if (cli.hasOption("encoding")) {
    String encoding = cli.getOptionValue("encoding")
    log.debug("Pulled '{}' from the '--encoding' cli option.", encoding)
    context.setVariable("CLI_LOG_ENCODING", encoding)
}

// The zBuilder expects an integer return code, usable in subsequent
// step `condition:` expressions.
return 0
