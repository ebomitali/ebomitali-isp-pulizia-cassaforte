// scripts/test/TestEnvironmentChain.groovy
def c = new EnvironmentChain()

// Predecessor (for DELETE_PREV_ENV_AFTER_BUILD)
assert c.getPredecessor('ST')  == 'ATO'
assert c.getPredecessor('SAD') == 'ATO'
assert c.getPredecessor('PR')  == 'ST'
assert c.getPredecessor('PA')  == 'SAD'
assert c.getPredecessor('ATO') == null : "ATO has no predecessor"

// Superior environments (for SFILAMENTO lookup)
assert c.getSuperiors('ST')  == ['PR', 'PA']
assert c.getSuperiors('SAD') == ['PR', 'PA']
assert c.getSuperiors('PR')  == []

// C1STAGE lookup
assert c.getStage('ATO') == 'O1'
assert c.getStage('ST')  == c.getStage('SAD') : "same stage family"
assert c.getStage('PR')  == 'P1'

// Capability flags
assert  c.requiresPrevEnvClean('ST')
assert  c.requiresPrevEnvClean('PR')
assert !c.requiresPrevEnvClean('ATO')
assert  c.supportsSfilamento('ST')
assert  c.supportsSfilamento('SAD')
assert !c.supportsSfilamento('ATO')
assert !c.supportsSfilamento('PR')

// getStage throws on unknown env
try {
    c.getStage('UNKNOWN')
    assert false : "should have thrown"
} catch (IllegalArgumentException e) {
    assert e.message.contains('UNKNOWN')
}

println "TestEnvironmentChain: PASS"
