class PuliziaFixture {

    final File dbbBuildDir
    final File dbbHomeDir
    final File appDir
    final File zosSimDir

    PuliziaFixture(File dbbBuildDir, File dbbHomeDir, File appDir, File zosSimDir) {
        this.dbbBuildDir = dbbBuildDir
        this.dbbHomeDir = dbbHomeDir
        this.appDir = appDir
        this.zosSimDir = zosSimDir
    }

    private File rulesFile() {
        new File(dbbBuildDir, 'build-data/rules.csv')
    }

    private File stageMapFile() {
        new File(dbbBuildDir, 'build-data/stagemap.csv')
    }

    void writeRules(String rulesCsvContent) {
        def f = rulesFile()
        f.parentFile.mkdirs()
        f.text = rulesCsvContent
    }

    void writeStageMap(File stageMapFixture) {
        def f = stageMapFile()
        f.parentFile.mkdirs()
        f.text = stageMapFixture.text
    }

    void writeConfig(String buildMapPath, String buildMapClientType = 'json') {
        def props = new Properties()
        props.setProperty('fileOpsType', 'macos')
        props.setProperty('buildMapClientType', buildMapClientType)
        props.setProperty('buildMapPath', buildMapPath)
        props.setProperty('uxBasedir', zosSimDir.absolutePath)
        props.setProperty('rulesPath', rulesFile().absolutePath)
        props.setProperty('stageMapPath', stageMapFile().absolutePath)
        appDir.mkdirs()
        def cfgFile = new File(appDir, 'PuliziaCassaforte.properties')
        cfgFile.withOutputStream { props.store(it, null) }
    }

    void deployRunPuliziaCassaforteToDbbBuild(File runPuliziaCassaforteFile) {
        def targetFile = new File(dbbBuildDir, 'groovy/pulizia-cassaforte/RunPuliziaCassaforte.groovy')
        targetFile.parentFile.mkdirs()
        targetFile.text = runPuliziaCassaforteFile.text
    }

    void deployFatSourceToDbbBuild(File fatSourceFile) {
        def targetFile = new File(dbbBuildDir, 'groovy/pulizia-cassaforte/FullPuliziaCassaforte.groovy')
        targetFile.parentFile.mkdirs()
        targetFile.text = fatSourceFile.text
    }

    File dataset(String dsName) {
        def dir = new File(zosSimDir, dsName)
        dir.mkdirs()
        dir
    }

    void member(File datasetDir, String name, String content = '') {
        new File(datasetDir, name).text = content
    }

    Script loadPostBuild(File postBuildFile, FakeTaskVariables config, FakeBuildContext context) {
        def shell = new GroovyShell(this.class.classLoader)
        def script = shell.parse(postBuildFile)
        script.config = config
        script.context = context
        script
    }

    Script loadPuliziaCassaforte(File puliziaCassaforteFile, FakeTaskVariables config, FakeBuildContext context) {
        def shell = new GroovyShell(this.class.classLoader)
        def script = shell.parse(puliziaCassaforteFile)
        script.config = config
        script.context = context
        script
    }
}
