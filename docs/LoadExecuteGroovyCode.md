# Load and Execute Groovy Code
In Groovy you've got several ways to load and execute code from a file at runtime, each with different tradeoffs around class loading, binding/scope, and reusability:

## 1. `GroovyShell.evaluate()`
Simplest option — parses and runs the script, returns the last expression's value.

```groovy
def shell = new GroovyShell()
def result = shell.evaluate(new File('/path/to/script.groovy'))
```

You can pass a `Binding` to inject/retrieve variables:

```groovy
def binding = new Binding()
binding.setVariable('inputParm', 'someValue')
def shell = new GroovyShell(binding)
shell.evaluate(new File('/path/to/script.groovy'))
def output = binding.getVariable('outputParm')
```

Good for one-off execution; not great for reuse since it recompiles every call.

## 2. `GroovyShell.parse()` → `Script` object
Gives you a reusable `Script` instance you can `run()` multiple times with different bindings.

```groovy
def shell = new GroovyShell()
Script script = shell.parse(new File('/path/to/script.groovy'))
script.binding = new Binding([param1: 'value1'])
def result = script.run()
```

## 3. `GroovyClassLoader`
What you're already using with `pulizia_cassaforte_full.groovy` — this is the right choice when the file defines a class (not just a script body), or when you need more control over the classloading (e.g. loading against a specific parent classloader, or loading many interdependent classes/files).

```groovy
def gcl = new GroovyClassLoader(this.class.classLoader)
Class clazz = gcl.parseClass(new File('/path/to/script.groovy'))
def instance = clazz.newInstance()
instance.someMethod()
```

If the file is a plain script (not a class), `parseClass` still works — Groovy wraps it in a synthetic `Script` subclass, and you instantiate + call `.run()`:

```groovy
def instance = clazz.newInstance()
instance.run()
```

This is the better fit for your `@BaseScript`/`TaskScript` pattern, since `GroovyClassLoader` respects `@BaseScript` annotations and lets the parsed class properly extend your base script type — `GroovyShell.evaluate()` can be flakier with custom base script classes depending on how the `CompilerConfiguration` is wired.

## 4. `GroovyScriptEngine`
Best when scripts reference/depend on each other (one script instantiates another from the same directory) and you want automatic reload-on-change during long-running processes (like a Jenkins-triggered DBB build).

```groovy
def gse = new GroovyScriptEngine(['/path/to/scripts/'] as String[])
def binding = new Binding()
gse.run('script.groovy', binding)
```

It tracks file timestamps and recompiles dependent scripts if they change on disk — useful in your DBB pipeline if multiple `.groovy` files are chained together as part of a build.

## 5. `Eval` (quick one-liners)
`Eval.me()`, `Eval.x()`, `Eval.xy()` — shorthand wrappers around `GroovyShell`, mainly good for trivial expressions rather than full script files.

---

### For your DBB/zBuilder context specifically
Given you're already dynamically loading fat `.groovy` files via `GroovyClassLoader` with `@BaseScript`/`TaskScript` injection, I'd stick with `GroovyClassLoader.parseClass()` + a `CompilerConfiguration` that sets the script base class, rather than switching to `GroovyShell`. It gives you:
- proper `@BaseScript` resolution
- control over the parent classloader (important in USS where classpath assembly can get messy)
- the ability to cache parsed classes if you're loading the same file repeatedly across build steps

If you want, tell me whether the target file is a plain script or defines a class, and whether you need the same file re-executed multiple times per build run (caching matters) — I can give you the exact `CompilerConfiguration`/`GroovyClassLoader` setup for that case.