class PuliziaTemporaneiFixture {

    final File dbbBuildDir
    final File dbbHomeDir
    final File appDir
    final File zosSimDir

    PuliziaTemporaneiFixture(File dbbBuildDir, File dbbHomeDir, File appDir, File zosSimDir) {
        this.dbbBuildDir = dbbBuildDir
        this.dbbHomeDir = dbbHomeDir
        this.appDir = appDir
        this.zosSimDir = zosSimDir
    }

    void writeConfig(String fileOpsType = 'macos') {
        def props = new Properties()
        props.setProperty('fileOpsType', fileOpsType)
        props.setProperty('uxBasedir', zosSimDir.absolutePath)
        appDir.mkdirs()
        def cfgFile = new File(appDir, 'PuliziaTemporanei.properties')
        cfgFile.withOutputStream { props.store(it, null) }
    }

    void deployJarToDbbBuild(File jarFile) {
        def targetFile = new File(dbbBuildDir, 'groovy/pulizia-temporanei/lib/pulizia-temporanei.jar')
        targetFile.parentFile.mkdirs()
        jarFile.withInputStream { input ->
            targetFile.withOutputStream { output ->
                output << input
            }
        }
    }

    void deployRunPuliziaTemporaneiToDbbBuild(File runPuliziaTemporaneiFile) {
        def targetFile = new File(dbbBuildDir, 'groovy/pulizia-temporanei/RunPuliziaTemporanei.groovy')
        targetFile.parentFile.mkdirs()
        targetFile.text = runPuliziaTemporaneiFile.text
    }

    void deployShellScriptToDbbBuild(File shellScriptFile) {
        def targetFile = new File(dbbBuildDir, 'groovy/pulizia-temporanei/run-pulizia-temporanei.sh')
        targetFile.parentFile.mkdirs()
        targetFile.text = shellScriptFile.text
        targetFile.setExecutable(true)
    }

    File dataset(String dsName) {
        def dir = new File(zosSimDir, dsName)
        dir.mkdirs()
        dir
    }

    void member(File datasetDir, String name, String content = '') {
        new File(datasetDir, name).text = content
    }
}
