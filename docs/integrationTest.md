Two types of integraton test:
- program run by jenkins using groovy at $DBB_HOME/bin/groovyz
- task/step run by dbb using groovy at $DBB_HOME/bin/groovyz

# integration test via sh
Purpouse test program run by jenkins via shell invocation
It uses a shell script to launch the program as would do Jenkins
## context setup:
- define a fat source merging all sources under src/main into single source script
- define a front-end groovy script file that will check arguments and load the single script and run the main class. Convention assume that if the front-end is Run<something> the main script is <something>Impl
- the spock integration test will use ProcessBuilder and an auxiliary shell script to launch the front-end script file
- auxiliary script will use groovyz if available or groovy; however groovy may require adding a classpath
- spock will copy front end file, fat source file and other auxiliary files to a unique working dir. It will setup context, that is file to manipulate, environment variables required to run the tests


# integration test on dbb task/step
Purpouse is to test the code within a dbb step or task. This will happen via use of groovyz interpreter and a front-end with "@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript" annotation
##  context setup:
- define a fat source merging all sources under src/main into single source script
- define a front-end groovy script file that will check arguments and load the single script and run the main class. Convention assume that if the front-end is Run<something> the main script is <something>Impl. The front-end will be annotated with "@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript" that make available the global log, context, config variables used to pass build information
- the spock integration test will provide log variable for logging, context and config prepopulated variable, will invoce the script on groovyz interpreter
- spock will define "DBB_BUILD" working directory and copy front end file, fat source file and other auxiliary files to $DBB_BUILD/groovy. It will make available context, config and log variables and will run the front-end script