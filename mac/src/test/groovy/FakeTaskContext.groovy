import com.ibm.dbb.task.TaskVariables
import com.ibm.dbb.task.BuildContext

class FakeTaskVariables extends TaskVariables {
    Map<String, String> vars = [:]

    @Override
    Object get(String name) {
        vars[name]
    }
}

class FakeBuildContext extends BuildContext {
    Map<String, String> vars = [:]

    @Override
    Object get(String name) {
        vars[name]
    }
}
