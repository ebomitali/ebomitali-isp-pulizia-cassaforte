// scripts/test/TestLocalBuildMapClient.groovy

// Locate the fixture file relative to this script
def fixtureFile = new File(getClass().protectionDomain.codeSource.location.toURI())
    .parentFile
    .toPath()
    .resolve("fixtures/buildmap.json")
    .toFile()
    .canonicalPath

def client = new LocalBuildMapClient(fixtureFile)

// Test 1: mapasm source — expect exactly 1 result with correct library and member
def sourcePath = "/dbb/DEE/IBM/yn_r_01_ato_r1/src/mapasm/batch/mapobj.asm"
def buildGroup = "yn_r_01_ato_r1"
def results = client.getGeneratedObjects(sourcePath, buildGroup)
assert results.size() == 1, "Expected 1 result for mapasm source, got ${results.size()}"
assert results[0].library == "LTM00.D9PO1.PE000.LING.MAP@@@@@.@@.COPY", "Wrong library: ${results[0].library}"
assert results[0].member == "MAPOBJ", "Wrong member: ${results[0].member}"

// Test 2: unknown path — expect empty list
def unknownResults = client.getGeneratedObjects("/dbb/DEE/IBM/yn_r_01_ato_r1/src/unknown/file.cbl", buildGroup)
assert unknownResults == [], "Expected empty list for unknown path, got ${unknownResults}"

println "TestLocalBuildMapClient: PASS"
