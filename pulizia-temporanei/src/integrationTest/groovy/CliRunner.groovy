import groovy.util.logging.Slf4j
import java.util.concurrent.TimeUnit

class CliResult {
    int exitCode
    String stdout
    String stderr
}

@Slf4j
class CliRunner {
    static CliResult run(File workDir, Map<String, String> env, List<String> args,
                         long timeoutSeconds = 60) {
        def java   = System.getProperty('java.executable')
        def cp     = System.getProperty('wrapper.classpath')
        def script = System.getProperty('wrapper.script')

        // Build the exact command line, then hand it to a shell so we test
        // the shell boundary the way a user (or JCL/USS caller) would hit it.
        def quotedArgs = args.collect { "'${it.replace("'", "'\\''")}'" }.join(' ')
        def cmd = "'${java}' -cp '${cp}' groovy.ui.GroovyMain '${script}' ${quotedArgs}"
        log.info("Running command: ${cmd} in workDir: ${workDir}")

        def pb = new ProcessBuilder('sh', '-c', cmd).directory(workDir)
        pb.environment().putAll(env ?: [:])
        def proc = pb.start()

        def out = new StringWriter()
        def err = new StringWriter()
        proc.consumeProcessOutput(out, err)   // drains both streams in threads

        if (!proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            throw new IllegalStateException("Wrapper timed out after ${timeoutSeconds}s")
        }
        new CliResult(exitCode: proc.exitValue(),
                      stdout: out.toString(), stderr: err.toString())
    }

    static CliResult runShellScript(File workDir, File shellScript, Map<String, String> env, List<String> args,
                                     long timeoutSeconds = 60) {
        def quotedArgs = args.collect { "'${it.replace("'", "'\\''")}'" }.join(' ')
        def cmd = "'${shellScript.absolutePath}' ${quotedArgs}"
        log.info("Running shell script: ${cmd} in workDir: ${workDir}")

        def pb = new ProcessBuilder('sh', '-c', cmd).directory(workDir)
        pb.environment().putAll(env ?: [:])
        def proc = pb.start()

        def out = new StringWriter()
        def err = new StringWriter()
        proc.consumeProcessOutput(out, err)

        if (!proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            throw new IllegalStateException("Shell script timed out after ${timeoutSeconds}s")
        }
        new CliResult(exitCode: proc.exitValue(),
                      stdout: out.toString(), stderr: err.toString())
    }
}
