import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.SpecInfo
import org.spockframework.runtime.model.FieldInfo
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes

/**
 * Global Spock extension that handles org.junit.jupiter.api.io.TempDir
 * for specs that use it (JUnit Jupiter @TempDir is not natively supported by Spock).
 */
class JUnitTempDirExtension implements IGlobalExtension {

    @Override
    void visitSpec(SpecInfo spec) {
        def tempDirFields = spec.allFields.findAll { FieldInfo field ->
            field.reflection.isAnnotationPresent(org.junit.jupiter.api.io.TempDir)
        }
        if (tempDirFields.isEmpty()) return

        spec.addSetupInterceptor(new IMethodInterceptor() {
            @Override
            void intercept(IMethodInvocation invocation) throws Throwable {
                tempDirFields.each { FieldInfo field ->
                    def tmpDir = Files.createTempDirectory('spock-junit-tempdir-')
                    field.writeValue(invocation.instance, tmpDir)
                }
                invocation.proceed()
            }
        })

        spec.addCleanupInterceptor(new IMethodInterceptor() {
            @Override
            void intercept(IMethodInvocation invocation) throws Throwable {
                tempDirFields.each { FieldInfo field ->
                    def tmpDir = field.readValue(invocation.instance) as Path
                    if (tmpDir != null && Files.exists(tmpDir)) {
                        try {
                            Files.walkFileTree(tmpDir, new SimpleFileVisitor<Path>() {
                                @Override
                                FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                    Files.delete(file)
                                    FileVisitResult.CONTINUE
                                }
                                @Override
                                FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                                    if (exc != null) throw exc
                                    Files.delete(dir)
                                    FileVisitResult.CONTINUE
                                }
                            })
                        } catch (IOException ignored) {
                            // best-effort cleanup; don't fail the test on teardown errors
                        }
                    }
                }
                invocation.proceed()
            }
        })
    }
}
