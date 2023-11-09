package de.tum.in.www1.artemis.service.connectors.aeolus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a windfile, the definition file for an aeolus build plan that
 * can then be used to generate a Bamboo build plan or a Jenkinsfile.
 */
public class Windfile {

    private String api;

    private WindfileMetadata metadata;

    private List<Action> actions;

    private Map<String, AeolusRepository> repositories;

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public WindfileMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(WindfileMetadata metadata) {
        this.metadata = metadata;
    }

    public List<Action> getActions() {
        return actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }

    /**
     * Sets the id of the windfile. If no metadata is present, also sets the metadata.
     *
     * @param id the id of the windfile, which corresponds to the id of the build plan or Jenkinsfile
     *               that is generated from this windfile
     */
    public void setId(String id) {
        if (this.metadata == null) {
            this.metadata = new WindfileMetadata();
        }
        this.metadata.setId(id);
    }

    /**
     * Gets the script actions of a windfile.
     *
     * @return the script actions of a windfile.
     */
    public List<ScriptAction> getScriptActions() {
        List<ScriptAction> scriptActions = new ArrayList<>();
        for (Action action : actions) {
            if (action instanceof ScriptAction) {
                scriptActions.add((ScriptAction) action);
            }
        }
        return scriptActions;
    }

    public void setGitCredentials(String credentials) {
        this.metadata.setGitCredentials(credentials);
    }

    public void setRepositories(Map<String, AeolusRepository> repositories) {
        this.repositories = repositories;
    }
}
