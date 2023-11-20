package de.tum.in.www1.artemis.service.connectors.aeolus;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base class for the actions that can be defined in a {@link Windfile}
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "class")
@JsonSubTypes({ @JsonSubTypes.Type(value = ScriptAction.class, name = "script-action"), @JsonSubTypes.Type(value = PlatformAction.class, name = "platform-action") })
public abstract class Action {

    private String name;

    private Map<String, Object> parameters;

    private Map<String, Object> environment;

    private boolean runAlways;

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

    public boolean isRunAlways() {
        return runAlways;
    }

    public void setRunAlways(boolean runAlways) {
        this.runAlways = runAlways;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
