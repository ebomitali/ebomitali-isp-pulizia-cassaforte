# Groovy Front End script
These scripts are meant to be launched via

```sh
groovyz -cp <path to pulizia-cassaforte jar> <script name> <arguments>
```

or as a script for a step of type task

```yaml
  - step: DoSomethingWithGroovy
    type: task
    script: groovy/DoSomethingWithGroovy.groovy
```
