package de.tum.in.www1.artemis.service.connectors.aeolus;

import java.util.List;
import java.util.Map;

/**
 * Base class for the actions that can be defined in a {@link Windfile}
 */
public abstract class Action {

    private String name;

    private Map<String, Object> parameters;

    private Map<String, Object> environment;

    private List<AeolusResult> results;

    private String workdir;

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

    public List<AeolusResult> getResults() {
        return results;
    }

    public void setResults(List<AeolusResult> results) {
        this.results = results;
    }

    public String getWorkdir() {
        return workdir;
    }

    public void setWorkdir(String workdir) {
        this.workdir = workdir;
    }
}
