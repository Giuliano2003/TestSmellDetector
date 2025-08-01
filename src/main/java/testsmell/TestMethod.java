package testsmell;

import java.util.HashMap;
import java.util.Map;

public class TestMethod extends SmellyElement {

    private String methodName;
    private boolean hasSmell;
    private Map<String, String> data;

    public TestMethod(String methodName) {
        this.methodName = methodName;
        data = new HashMap<>();
    }

    public TestMethod(String methodName, boolean hasSmell) {
        this.methodName = methodName;
        this.hasSmell = hasSmell;
    }

    public void setSmell(boolean hasSmell) {
        this.hasSmell = hasSmell;
    }



    public void addDataItem(String name, String value) {
        data.put(name, value);
    }

    @Override
    public String getElementName() {
        return methodName;
    }

    @Override
    public boolean isSmelly() {
        return hasSmell;
    }

    @Override
    public Map<String, String> getData() {
        return data;
    }
}
