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
    // Object-valued: BUILD_GROUP may hold a real com.ibm.dbb.metadata.BuildGroup (mock or
    // otherwise), not just a String.
    Map<String, Object> vars = [:]

    @Override
    Object get(String name) {
        vars[name]
    }
}
