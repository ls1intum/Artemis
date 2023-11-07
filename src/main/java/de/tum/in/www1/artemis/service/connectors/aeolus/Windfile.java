package de.tum.in.www1.artemis.service.connectors.aeolus;

import java.util.*;

/**
 * Represents a windfile, the definition file for an aeolus build plan that
 * can then be used to generate a Bamboo build plan or a Jenkinsfile.
 */
public class Windfile {

    private String api;

    private Metadata metadata;

    private List<Action> actions;

    private Map<String, AeolusRepository> repositories;

    /**
     * Converts the serialized form of a windfile ({@link AeolusDefinition}) to a
     * windfile object that can be used to generate a Bamboo build plan or a Jenkinsfile.
     *
     * @param aeolusDefinition the serialized form of a windfile
     * @return the windfile object
     */
    public static Windfile toWindfile(AeolusDefinition aeolusDefinition) {
        Windfile windfile = new Windfile();
        windfile.setApi(aeolusDefinition.getApi());
        windfile.setMetadata(aeolusDefinition.getMetadata());
        List<Action> actions = new ArrayList<>();
        for (SerializedAction action : aeolusDefinition.getActions()) {
            if (action.getScript() != null) {
                actions.add(ScriptAction.deserialize(action));
            }
            else {
                actions.add(PlatformAction.deserialize(action));
            }
        }

        windfile.actions = actions;
        return windfile;
    }

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public List<Action> getActions() {
        return actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }

    public Map<String, AeolusRepository> getRepositories() {
        return repositories;
    }

    public void setRepositories(Map<String, AeolusRepository> repositories) {
        this.repositories = repositories;
    }

    public void setId(String id) {
        this.metadata.setId(id);
    }

    public void setGitCredentials(String credentials) {
        this.metadata.setGitCredentials(credentials);
    }
}
