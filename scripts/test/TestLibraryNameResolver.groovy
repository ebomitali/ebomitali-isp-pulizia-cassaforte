// scripts/test/TestLibraryNameResolver.groovy
def r = new LibraryNameResolver()

// Parameter substitution
assert r.resolve('LTM00.D9P${C1STAGE}.PE000.LING.COB@@@@@.@@.COPY', 'O1', '') \
    == 'LTM00.D9PO1.PE000.LING.COB@@@@@.@@.COPY'

assert r.resolve('LTM00.D9P${C1STAGE}.PE000.SYST.${C1SYSTEM}@@@@@@@.BT.LOAD', 'S1', 'MYSYS') \
    == 'LTM00.D9PS1.PE000.SYST.MYSYS@@@@@@@.BT.LOAD'

// TOCOLB derivation: 4th qualifier @@@@→TO@@, 5th qualifier @@@@@@@@→COLB@@@@
assert r.toTocolbLibrary('LTM00.D9PS1.PE000.@@@@.@@@@@@@@.@@.SJCL') \
    == 'LTM00.D9PS1.PE000.TO@@.COLB@@@@.@@.SJCL'

// Library without @@@@ pattern passes through unchanged
assert r.toTocolbLibrary('LTM00.D9PS1.PE000.LING.COB@@@@@.@@.COPY') \
    == 'LTM00.D9PS1.PE000.LING.COB@@@@@.@@.COPY'

println "TestLibraryNameResolver: PASS"
