import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import java.nio.file.*

/**
 * Spec for jobz/jobs file type processing.
 *
 * Jobz paths have the form {@code <dir>/<member>.<ext>} without the standard
 * underscore-delimited application segment; layer operativo is hardcoded to {@code 01}
 * and {@code C1SYSTEM} is empty.  The extension must appear in {@code jobzExtensions}.
 *
 * For the S (sfilamento) action, restore copies from the superior environment's cassaforte
 * library into the current environment's TOCOLB library (same transformation as SJCL* types).
 */
class JobzSpec extends Specification {

    static final String ENV_ST       = 'ST'
    static final String SOURCE_PATH  = 'edux0-jobz/$HXQ001.STWSNCS'
    static final String MEMBER       = '$HXQ001'
    static final String ST_JOBZ_LIB  = 'LTM00.D9PXAD.PE000.@@@@.@@@@@@@@.@@.JOBZ'
    static final String PR_JOBZ_LIB  = 'LTM00.D9PXPE.PE000.@@@@.@@@@@@@@.@@.JOBZ'
    static final String MEMBER_2     = '$HXQ002'
    static final String ATO_JNCS_LIB = 'LTM00.D9PX2A.PE000.@@@@.@@@@@@@@.@@.JNCS'
    static final String ST_JNCS_LIB  = 'LTM00.D9PXAD.PE000.@@@@.@@@@@@@@.@@.JNCS'
    static final String PR_JNCS_LIB  = 'LTM00.D9PXPE.PE000.@@@@.@@@@@@@@.@@.JNCS'

    @TempDir
    Path tempDir

    File bmFile
    LocalFileOps ops

    def setup() {
        bmFile = new File(getClass().getResource('/fixtures/buildmap.json').toURI())
        ops    = new LocalFileOps(tempDir.toString())
    }

    def "C action on jobz path deletes member from cassaforte library"() {
        given:
        createMember(ST_JOBZ_LIB, MEMBER, 'st-content')
        def lista = listFile("C,${SOURCE_PATH}")

        when:
        def errors = runImpl(lista, ENV_ST)

        then:
        errors == 0
        !ops.exists("//${ST_JOBZ_LIB}(${MEMBER})")
    }

    def "C action on jobz path is idempotent when member is absent"() {
        given:
        def lista = listFile("C,${SOURCE_PATH}")

        expect:
        runImpl(lista, ENV_ST) == 0
    }

    def "S action on jobz path in ST deletes ST cassaforte member and restores from PR into ST TOCOLB"() {
        given:
        createMember(ST_JOBZ_LIB, MEMBER, 'st-content')
        createMember(PR_JOBZ_LIB, MEMBER, 'pr-content')
        def lista = listFile("S,${SOURCE_PATH}")

        when:
        def errors = runImpl(lista, ENV_ST)

        then:
        errors == 0
        !ops.exists("//${ST_JOBZ_LIB}(${MEMBER})")
        ops.exists('//LTM00.D9PXAD.PE000.TO@@.COLB@@@@.@@.JOBZ(' + MEMBER + ')')
    }

    def "S action on jobz JNCS path in ATO deletes ATO cassaforte member (no restore)"() {
        given:
        createMember(ATO_JNCS_LIB, MEMBER, 'ato-content')
        def lista = listFile("S,${SOURCE_PATH}")

        when:
        def errors = runImpl(lista, 'ATO')

        then:
        errors == 0
        !ops.exists("//${ATO_JNCS_LIB}(${MEMBER})")
        !ops.exists('//LTM00.D9PX2A.PE000.TO@@.COLB@@@@.@@.JNCS(' + MEMBER + ')')
    }

    def "S action on jobz JNCS path in ST deletes ST cassaforte member and restores from PR into ST TOCOLB"() {
        given:
        createMember(ST_JNCS_LIB, MEMBER_2, 'st-content')
        createMember(PR_JNCS_LIB, MEMBER_2, 'pr-content')
        def lista = listFile("S,edux0-jobz/\$HXQ002.STWSNCS")

        when:
        def errors = runImpl(lista, ENV_ST)

        then:
        errors == 0
        !ops.exists("//${ST_JNCS_LIB}(${MEMBER_2})")
        ops.exists('//LTM00.D9PXAD.PE000.TO@@.COLB@@@@.@@.JNCS(' + MEMBER_2 + ')')
    }

    def "S action on jobz path in non-ST environment only deletes (no restore)"() {
        given:
        createMember(PR_JOBZ_LIB, MEMBER, 'pr-content')
        def lista = listFile("S,${SOURCE_PATH}")

        when:
        def errors = runImpl(lista, 'PR')

        then:
        errors == 0
        !ops.exists("//${PR_JOBZ_LIB}(${MEMBER})")
        !ops.exists('//LTM00.D9PXPE.PE000.TO@@.COLB@@@@.@@.JOBZ(' + MEMBER + ')')
    }

    def "S action on jobz path with no superior member only deletes (no restore)"() {
        given:
        createMember(ST_JOBZ_LIB, MEMBER, 'st-content')
        def lista = listFile("S,${SOURCE_PATH}")

        when:
        def errors = runImpl(lista, ENV_ST)

        then:
        errors == 0
        !ops.exists("//${ST_JOBZ_LIB}(${MEMBER})")
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private int runImpl(String lista, String env) {
        def impl = new PuliziaCassaforteImpl()
        impl.rulesPath    = new File(getClass().getResource('/fixtures/rules.csv').toURI()).canonicalPath
        impl.stageMapPath = new File(getClass().getResource('/fixtures/stagemap.csv').toURI()).canonicalPath
        def props = new Properties()
        props.setProperty('buildMapClientType', 'json')
        props.setProperty('buildMapPath', bmFile.canonicalPath)
        props.setProperty('fileOpsType', 'local')
        props.setProperty('uxBasedir', tempDir.toString())
        props.setProperty('jobzExtensions', 'STWSNCS')
        impl.run(lista, env, env, props)
    }

    private void createMember(String lib, String member, String content = 'content') {
        def path = tempDir.resolve("${lib}/${member}")
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

    private String listFile(String content) {
        def f = tempDir.resolve('lista.csv').toFile()
        f.text = content
        f.canonicalPath
    }
}
