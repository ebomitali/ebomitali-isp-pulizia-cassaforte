@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript

import com.ibm.dbb.task.TaskConstants
import java.text.SimpleDateFormat

def cli = context.getCommandLine(TaskConstants.COMMAND_LINE)

if (cli.hasOption("branch")) {
    String branch = cli.getOptionValue("branch")
    log.debug("Pulled '{}' from the '--branch' cli option.", branch)
    context.setVariable("CLI_BRANCH", branch)
}

if (cli.hasOption("buildGroup")) {
    String buildGroup = cli.getOptionValue("buildGroup")
    log.debug("Pulled '{}' from the '--buildGroup' cli option.", buildGroup)
    context.setVariable("CLI_BUILDGROUP", buildGroup)
}

if (cli.hasOption("comment")) {
    String comment = cli.getOptionValue("comment")
    log.debug("Pulled '{}' from the '--comment' cli option.", comment)
    context.setVariable("CLI_COMMENT", comment)
}

if (cli.hasOption("continueOnError")) {
    Boolean continueOnError = cli.getOptionValue("continueOnError").toBoolean()
    log.debug("Pulled '{}' from the '--continueOnError' cli option.", continueOnError)
    context.setVariable("CLI_CONTINUEONERROR", continueOnError)
}

if (cli.hasOption("environment")) {
    String environment = cli.getOptionValue("environment")
    log.debug("Pulled '{}' from the '--environment' cli option.", environment)
    context.setVariable("CLI_BUILDENV", environment)
}
    
if (cli.hasOption("proc")) {
    String proc = cli.getOptionValue("proc")
    log.debug("Pulled '{}' from the '--proc' cli option.", proc)
    context.setVariable("CLI_PROC", proc)
} else {
    log.warn("No value provided for '--proc' cli option. Defaulting to 'ALL'.")
    context.setVariable("CLI_PROC", "ALL")
}

if (cli.hasOption("twxxx")) {
    String twxxx = cli.getOptionValue("twxxx")
    log.debug("Pulled '{}' from the '--twxxx' cli option.", twxxx)
    context.setVariable("CLI_TWXXX", twxxx)
} else {
    log.warn("No value provided for '--twxxx' cli option. Defaulting to 'TW000'.")
    context.setVariable("CLI_TWXXX", "TW000")
}

if (cli.hasOption("udc")) {
    String udc = cli.getOptionValue("udc")
    log.debug("Pulled '{}' from the '--udc' cli option.", udc)
    context.setVariable("CLI_UDC", udc)
}

if (cli.hasOption("cli_uid")) {
    String cli_uid = cli.getOptionValue("cli_uid")
    log.debug("Pulled '{}' from the '--cli_uid' cli option.", cli_uid)
    context.setVariable("CLI_UID", cli_uid)
    startTime = context.getVariable("START_TIME")
    def outputFormatter = new SimpleDateFormat("yyyyMMdd.HHmmss.SSS")
    def startTimeFormatted = outputFormatter.format(startTime)
    context.setVariable("CUSTOM_RESULT_LABEL", "build-" + cli_uid + "-" + startTimeFormatted)
}

if (cli.hasOption("user")) {
    String user = cli.getOptionValue("user")
    log.debug("Pulled '{}' from the '--user' cli option.", user)
    context.setVariable("CLI_USER", user)
}

// The zBuilder expects an integer return code as the returned value which can be used in subsequent conditions
return 0
