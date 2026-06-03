package com.ibm.dbb.groovy

abstract class ScriptLoader extends Script {
    Script loadScript(File file) {
        def gcl = new GroovyClassLoader(this.class.classLoader)
        Class clazz = gcl.parseClass(file)
        Script script = (Script) clazz.getDeclaredConstructor().newInstance()
        script.binding = new Binding()
        script.run()
        return script
    }
}
