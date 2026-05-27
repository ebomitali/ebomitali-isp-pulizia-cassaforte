// This script demonstrates loading and executing a Groovy script using the DBB ScriptLoader
// This script contains several class definition dependent one on the other to verify 
// that script loading and execution is working correctly
// Eventually it exposes a "factory method" createWorld() that is called 
// by the loader script to use access to defined classes and methods

// This class is stdalone
class Hello {
    void print() {
        println "hello"
    }
}

// This class depends on Hello and is used to verify that mantains scope
// within the loader script execution
class World {
    void print() {
        Hello hello = new Hello()
        hello.print()
        println "world"
    }
}

def createWorld() {
    return World.newInstance()
}
