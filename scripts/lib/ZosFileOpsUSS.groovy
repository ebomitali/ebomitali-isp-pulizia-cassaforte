// scripts/lib/ZosFileOpsUSS.groovy
// Mainframe-only. Must be compiled and run with groovyz on z/OS USS.
// After upload to USS: chtag -tc IBM-1047 ZosFileOpsUSS.groovy
// ZFile javadoc https://www.ibm.com/docs/en/sdk-java-technology/8?topic=jzos-zfile
import com.ibm.jzos.ZFile
import com.ibm.jzos.ZFileException

class ZosFileOpsUSS implements ZosFileOps {

    boolean exists(String path) {
        if (path.startsWith('//')) {
            return ZFile.dsExists(mvsName(path))
        }
        new File(path).exists()
    }

    void delete(String path) {
        if (path.startsWith('//')) {
            def (dsn, member) = parseDsn(path)
            if (member) {
                // PDS member deletion via alloc+free with delete disposition.
                // TODO: verify behaviour with ISP team — on some z/OS configurations
                // FREE DELETE on a PDS member reference scratches the whole PDS.
                // Alternative: use an IEHPROGM step or TSO DELETE command via ZUtil.
                def ddname = 'D' + UUID.randomUUID().toString().replace('-', '').substring(0, 7).toUpperCase()
                ZFile.bpxwdyn("alloc fi(${ddname}) da('${dsn}(${member})') old msg(2)")
                ZFile.bpxwdyn("free fi(${ddname}) delete")
            } else {
                def ddname = 'D' + UUID.randomUUID().toString().replace('-', '').substring(0, 7).toUpperCase()
                ZFile.bpxwdyn("alloc fi(${ddname}) da('${dsn}') old msg(2)")
                ZFile.bpxwdyn("free fi(${ddname}) delete")
            }
            return
        }
        new File(path).delete()
    }

    void copy(String src, String dst) {
        if (src.startsWith('//') && dst.startsWith('//')) {
            def (srcDsn, srcMember) = parseDsn(src)
            def (dstDsn, dstMember) = parseDsn(dst)
            def srcSpec = srcMember ? "${srcDsn}(${srcMember})" : srcDsn
            def dstSpec = dstMember ? "${dstDsn}(${dstMember})" : dstDsn
            def srcFile = new ZFile("//'${srcSpec}'", 'rb,type=record')
            try {
                def dstFile = new ZFile("//'${dstSpec}'", 'wb,type=record')
                try {
                    def buf = new byte[32760]
                    int len
                    while ((len = srcFile.read(buf)) >= 0) {
                        dstFile.write(buf, 0, len)
                    }
                } finally {
                    dstFile.close()
                }
            } finally {
                srcFile.close()
            }
            return
        }
        // USS file copy
        new File(dst).bytes = new File(src).bytes
    }

    List<String> list(String container) {
        if (container.startsWith('//')) {
            return ZFile.listMembers(mvsName(container))?.toList() ?: []
        }
        new File(container).list()?.toList() ?: []
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    private String mvsName(String path) {
        def inner = path.substring(2)
        def m = (inner =~ /^(.+?)\((.+?)\)$/)
        m.matches() ? m.group(1) : inner
    }

    private List<String> parseDsn(String path) {
        def inner = path.substring(2)
        def m = (inner =~ /^(.+?)\((.+?)\)$/)
        m.matches() ? [m.group(1), m.group(2)] : [inner, null]
    }
}
