package fr.insee.trevas.lab.model;

import javax.script.Bindings;
import java.util.Map;

public class Body {

    private String vtlScript;
    private Bindings bindings;
    private Map<String, QueriesForBindings> queriesForBindings;
    private Map<String, QueriesForBindingsToSave> queriesForBindingsToSave;
    private Map<String, S3ForBindings> s3ForBindings;
    private ToSave toSave;

    private String writePath;

    public Map<String, S3ForBindings> getS3ForBindings() {
        return s3ForBindings;
    }

    public void setS3ForBindings(Map<String, S3ForBindings> s3ForBindings) {
        this.s3ForBindings = s3ForBindings;
    }

    public String getVtlScript() {
        return vtlScript;
    }

    public void setVtlScript(String vtlScript) {
        this.vtlScript = vtlScript;
    }

    public Bindings getBindings() {
        return bindings;
    }

    public void setBindings(Bindings bindings) {
        this.bindings = bindings;
    }

    public Map<String, QueriesForBindings> getQueriesForBindings() {
        return queriesForBindings;
    }

    public void setQueriesForBindings(Map<String, QueriesForBindings> queriesForBindings) {
        this.queriesForBindings = queriesForBindings;
    }

    public ToSave getToSave() {
        return toSave;
    }

    public void setToSave(ToSave toSave) {
        this.toSave = toSave;
    }

    public Map<String, QueriesForBindingsToSave> getQueriesForBindingsToSave() {
        return queriesForBindingsToSave;
    }

    public void setQueriesForBindingsToSave(Map<String, QueriesForBindingsToSave> queriesForBindingsToSave) {
        this.queriesForBindingsToSave = queriesForBindingsToSave;
    }

    public String getWritePath() {
        return writePath;
    }

    public void setWritePath(String writePath) {
        this.writePath = writePath;
    }
}
