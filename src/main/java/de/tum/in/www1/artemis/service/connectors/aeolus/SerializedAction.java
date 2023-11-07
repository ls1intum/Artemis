package de.tum.in.www1.artemis.service.connectors.aeolus;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an action that can be defined in a {@link Windfile}
 */
public class SerializedAction {

    private String name;

    @JsonProperty("run_always")
    private boolean runAlways;

    private String script;

    private String type;

    private String kind;

    private Map<String, Object> parameters;

    private Map<String, Object> environment;

    public SerializedAction() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isRunAlways() {
        return runAlways;
    }

    public void setRunAlways(boolean runAlways) {
        this.runAlways = runAlways;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Map<String, Object> getEnvironment() {
        return environment;
    }

    public void setEnvironment(Map<String, Object> environment) {
        this.environment = environment;
    }
}
