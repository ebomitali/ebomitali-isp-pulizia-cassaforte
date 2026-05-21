// scripts/PuliziaPostBuild.groovy
// DBB task wrapper — invoked by DBB Language pipeline as type:task step.
// Required TaskVariables: MEMBER, FILE_EXT, CLI_BUILDENV, CLI_BUILDGROUP
@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript

def member      = config.getStringVariable('MEMBER')
def fileExt     = config.getStringVariable('FILE_EXT')
def environment = config.getStringVariable('CLI_BUILDENV')
def buildGroup  = config.getStringVariable('CLI_BUILDGROUP') ?: config.getStringVariable('BUILDGROUP')

def sourcePath  = context.getBuildFile()

def rulesPath   = new File(context.getWorkingDirectory(), 'build-data/rules.csv').absolutePath
def ops         = new ZosFileOpsUSS()
def rules       = new DeletionRulesLoader().load(rulesPath)

// TODO: replace with DBB build-result map client when available
def bmFile      = new File(context.getWorkingDirectory(), 'build-data/buildmap.json')
def buildMap    = bmFile.exists()
    ? new LocalBuildMapClient(bmFile.absolutePath)
    : [getGeneratedObjects: { sp, bg -> [] }] as BuildMapClient

def stageMapPath = new File(context.getWorkingDirectory(), 'build-data/stage-map.csv').absolutePath
def stageMap     = new StageMapLoader().load(stageMapPath)
def deleteLogic  = new DeleteCassaforteLogic(ops: ops, rules: rules, buildMap: buildMap)
def prevClean    = new PrevEnvCleanLogic(
    deleteLogic: deleteLogic,
    extractor:   new PathVariableExtractor(),
    stageMap:    stageMap,
    hlq:         ''
)

def count = prevClean.execute(sourcePath, fileExt, environment, buildGroup)

println "PuliziaPostBuild: env=${environment} predecessor=${new EnvironmentChain().getPredecessor(environment)} deleted=${count}"

return 0   // DBB requires Integer return — any other type triggers BGZZB0043W warning with RC 0
