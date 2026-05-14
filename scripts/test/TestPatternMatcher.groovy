// scripts/test/TestPatternMatcher.groovy
def m = new PatternMatcher()

assert  m.matches('%CPYCOB*', 'ACPYCOB ')   : "% = one char, * = zero-or-more"
assert  m.matches('%CPYCOB*', 'XCPYCOBABC') : "* matches multiple chars"
assert  m.matches('SZFSSWG ', 'SZFSSWG ')   : "exact match with trailing space"
assert !m.matches('SZFSSWG ', 'SZFSSWGX')   : "wrong 8th char"
assert  m.matches('%CB2%R  ', 'ACB2XR  ')   : "two % wildcards"
assert !m.matches('%CB2%R  ', 'ACB2XRY ')   : "extra char before trailing spaces"
assert  m.matches('SJCL*',    'SJCL    ')   : "SJCL* matches SJCL with spaces"
assert  m.matches('SJCL*',    'SJCLPROC')   : "SJCL* matches SJCLPROC"
assert !m.matches('SJCL*',    'XJCL    ')   : "does not match different prefix"
assert  m.matches('*',        'ANYTHING')   : "* alone matches anything"

println "TestPatternMatcher: PASS"
