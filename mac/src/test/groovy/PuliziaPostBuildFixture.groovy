class PuliziaPostBuildFixture {

    final File cwd
    final File dbbBuildDir
    final File zosSimDir

    PuliziaPostBuildFixture(File cwd, File dbbBuildDir, File zosSimDir) {
        this.cwd = cwd
        this.dbbBuildDir = dbbBuildDir
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

    void writeConfig(String buildMapPath) {
        def props = new Properties()
        props.setProperty('fileOpsType', 'macos')
        props.setProperty('buildMapClientType', 'json')
        props.setProperty('buildMapPath', buildMapPath)
        props.setProperty('uxBasedir', zosSimDir.absolutePath)
        props.setProperty('rulesPath', rulesFile().absolutePath)
        props.setProperty('stageMapPath', stageMapFile().absolutePath)
        def cfgFile = new File(cwd, 'PuliziaCassaforte.properties')
        cfgFile.withOutputStream { props.store(it, null) }
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
}
