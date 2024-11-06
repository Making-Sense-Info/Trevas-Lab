package fr.insee.trevas.lab.model;

import java.util.Map;

public class BodyProvenance {

    String id;
    String name;
    String script;

    private Map<String, S3ForBindings> bindings;

    public String getScript() {
        return script;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, S3ForBindings> getBindings() {
        return bindings;
    }
}
