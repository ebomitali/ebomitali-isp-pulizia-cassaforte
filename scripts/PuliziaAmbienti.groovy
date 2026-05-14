// scripts/PuliziaAmbienti.groovy
// DBB task wrapper — invoked by DBB Language pipeline as type:task step.
// Required TaskVariables: MEMBER, FILE_EXT, CLI_BUILDENV, CLI_BUILDGROUP
// Optional TaskVariables: C1SYSTEM (defaults to derivation from build group)
@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript

def member      = config.getStringVariable('MEMBER')
def fileExt     = config.getStringVariable('FILE_EXT')
def environment = config.getStringVariable('CLI_BUILDENV')
def buildGroup  = config.getStringVariable('CLI_BUILDGROUP') ?: config.getStringVariable('BUILDGROUP')
def system      = config.getStringVariable('C1SYSTEM') ?: buildGroup?.tokenize('_')?.first()?.toUpperCase() ?: ''

def sourcePath  = context.getBuildFile()

def rulesPath   = new File(context.getWorkingDirectory(), 'build-data/rules.csv').absolutePath
def ops         = new ZosFileOpsUSS()
def rules       = new DeletionRulesLoader().load(rulesPath)

// TODO: replace with DBB build-result map client when available
def bmFile      = new File(context.getWorkingDirectory(), 'build-data/buildmap.json')
def buildMap    = bmFile.exists()
    ? new LocalBuildMapClient(bmFile.absolutePath)
    : [getGeneratedObjects: { sp, bg -> [] }] as BuildMapClient

def deleteLogic = new DeleteCassaforteLogic(ops: ops, rules: rules, buildMap: buildMap)
def prevClean   = new PrevEnvCleanLogic(deleteLogic: deleteLogic)

def count = prevClean.execute(sourcePath, fileExt, environment, system, buildGroup)

println "PuliziaAmbienti: env=${environment} predecessor=${new EnvironmentChain().getPredecessor(environment)} deleted=${count}"

return 0   // DBB requires Integer return — any other type triggers BGZZB0043W warning with RC 0
