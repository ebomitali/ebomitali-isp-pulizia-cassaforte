class PuliziaTemporaneiFixture {

    final File workDir
    final File zosSimDir

    PuliziaTemporaneiFixture(File workDir, File zosSimDir) {
        this.workDir = workDir
        this.zosSimDir = zosSimDir
    }

    void writeConfig(String fileOpsType = 'macos') {
        def props = new Properties()
        props.setProperty('fileOpsType', fileOpsType)
        props.setProperty('uxBasedir', zosSimDir.absolutePath)
        workDir.mkdirs()
        def cfgFile = new File(workDir, 'PuliziaTemporanei.properties')
        cfgFile.withOutputStream { props.store(it, null) }
    }

    void deployRunPuliziaTemporaneiToWorkDir(File runPuliziaTemporaneiFile) {
        def targetFile = new File(workDir, 'RunPuliziaTemporanei.groovy')
        targetFile.parentFile.mkdirs()
        targetFile.text = runPuliziaTemporaneiFile.text
    }

    void deployFullPuliziaTemporaneiToWorkDir(File fullPuliziaTemporaneiFile) {
        def targetFile = new File(workDir, 'FullPuliziaTemporanei.groovy')
        targetFile.parentFile.mkdirs()
        targetFile.text = fullPuliziaTemporaneiFile.text
    }

    void deployShellScriptToWork(File shellScriptFile) {
        def targetFile = new File(workDir, 'run-pulizia-temporanei.sh')
        targetFile.parentFile.mkdirs()
        targetFile.text = shellScriptFile.text
        targetFile.setExecutable(true)
    }

    void deploySlf4jJar(Collection<File> jarFiles) {
        def libDir = new File(workDir, 'lib')
        libDir.mkdirs()
        jarFiles.each { jarFile ->
            if (jarFile && jarFile.exists()) {
                def targetFile = new File(libDir, jarFile.name)
                targetFile.bytes = jarFile.bytes
            }
        }
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
