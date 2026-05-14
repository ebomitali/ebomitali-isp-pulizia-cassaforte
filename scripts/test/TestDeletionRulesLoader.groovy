// scripts/test/TestDeletionRulesLoader.groovy
def loader = new DeletionRulesLoader()
def rules  = loader.load('test/fixtures/rules.csv')

assert rules.size() == 5 : "should load 5 rules (skip comment line)"
assert rules[0].typePattern     == '%CPYCOB*'
assert rules[0].libraryTemplate == 'LTM00.D9P${C1STAGE}.PE000.LING.COB@@@@@.@@.COPY'
assert rules[0].useBuildMap     == false
assert rules[1].useBuildMap     == true  : "SZFSSWG BUILD MAP rule"
assert rules[1].typePattern     == 'SZFSSWG '
assert rules[3].typePattern     == 'SJCL*   '

println "TestDeletionRulesLoader: PASS"
