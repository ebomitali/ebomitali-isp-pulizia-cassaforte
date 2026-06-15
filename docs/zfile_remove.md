# ZFile

## High level Architecture

**JZOS** is the Java-to-z/OS bridge — the `com.ibm.jzos.*` package shipped with the IBM z/OS Java SDK. It gives Java/Groovy code native access to z/OS services that plain Java has no concept of: dataset I/O (`ZFile`), PDS directories (`PdsDirectory`), dynamic allocation (`ZFile.bpxwdyn`), MVS console, system symbols, and so on. So when you call `ZFile.remove(...)` in your Groovy task, JZOS is the layer translating that Java call into a native z/OS operation.

**LE** is the **Language Environment** — IBM's common runtime that programs written in C/C++, COBOL, PL/I, and Assembler all run under on z/OS. It provides the shared runtime services those languages need (storage management, condition handling, and the C runtime library).

The connection between them: JZOS's `ZFile` doesn't reimplement dataset access from scratch — under the hood it calls the **LE C runtime library** (the `fopen`/`fclose`/`remove` family of functions). You can see this in the stack traces from JZOS — methods like `ZFile.fopen` are native calls down into LE's C I/O. So `ZFile.remove("//'HLQ.PDS(MEM)'")` ultimately becomes an LE C `remove()` call, and it's LE — not JZOS — that decides to do its own OLD allocation for the member.

## ZFile Remove
The reason `ZFile.remove()` ends up with exclusive control is that it does its own dynamic allocation of the target before deleting, and for a member it allocates the PDS with OLD. You can't change that on the `remove()` call itself — there's no disposition argument. But you can stop it from allocating at all by pre-allocating the PDS yourself with SHR and pointing `remove()` at that DD instead of at the DSN.

When the name you pass is a `//DD:` reference, LE reuses the existing allocation rather than doing its own OLD alloc, so the member STOW-delete inherits whatever disposition you allocated the DD with. This is the same mechanism as the classic IDCAMS pattern (`//DD1 DD DISP=SHR,DSN=…` + `DELETE 'pds(mem)' FILE(DD1)`) — there it's the `FILE()` reference, not the `DISP`, that does the work; the `//DD:` form is the JZOS/LE equivalent.

```groovy
import com.ibm.jzos.ZFile

String dsn    = "HLQ.MY.PDS"     // PDS only, no member here
String member = "MEMB1"
String ddname = "DELDD"

// 1. Allocate the PDS with DISP=SHR
ZFile.bpxwdyn("alloc fi(${ddname}) da('${dsn}') shr msg(2)")
try {
    // 2. Delete the member via the pre-allocated SHR DD
    //    LE reuses this allocation instead of doing its own OLD alloc
    ZFile.remove("//DD:${ddname}(${member})")
} finally {
    // 3. Always free
    ZFile.bpxwdyn("free fi(${ddname})")
}
```

A few notes for your cassaforte scripts:

You can swap `ZFile.bpxwdyn(...)` for the `com.ibm.dbb.build.internal.Utils.bpxwdyn(...)` wrapper you're already using elsewhere — same effect. Keep the alloc on the PDS itself (`da('${dsn}')`), not on `dsn(member)`; you select the member only in the `remove()` call.

The DD-reference form sidesteps the disposition issue, but be aware of what SHR actually buys you. The STOW directory update is still serialized internally by the system, so SHR is safe; what it changes is that you no longer take an exclusive ENQ on the whole library, so concurrent readers aren't locked out. The flip side, if the cassaforte libraries are PDSEs in the LNKLST (or otherwise have active connections), is that a deleted member can go into *pending delete* state and won't release its space until every connection drops — that's a PDSE characteristic independent of your disposition choice, but SHR makes concurrent connections more likely, so worth watching if you're tracking space.

I'd verify the exact `//DD:` resolution on-host with one of your `tsocmd`/log iterations before wiring it into both scripts — `remove()` on a `DD:` reference is well-established for fopen-family resolution, but it's worth a single confirmation run given how central deletion is to the C/S scenarios.

One alternative worth keeping in mind: if you ever want this as a pipeline step rather than inside Groovy, the IDCAMS `DELETE … FILE(dd)` pattern as a `type: mvs` step gives you the same SHR semantics declaratively.

JZOS uses LE and that's why the `//DD:ddname` trick works. The LE C runtime's filename resolution recognizes the `//DD:` prefix as "use this already-allocated DD rather than allocating the dataset yourself." It's an LE convention (the same one that lets C programs reference JCL DD statements), and JZOS inherits it for free because it's just passing the name string through to LE. Pre-allocating with SHR and handing LE a `//DD:` name is what stops it from taking its own exclusive allocation.