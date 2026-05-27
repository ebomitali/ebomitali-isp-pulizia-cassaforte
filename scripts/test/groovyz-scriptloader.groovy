// This script demonstrates loading and executing a Groovy script using the DBB ScriptLoader
// It loads an external script, creates a world object, and prints its output
@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript

def loaded =loadScript(new File("groovyz-scriptloaded.groovy"))
def world = loaded.createWorld()
world.print()