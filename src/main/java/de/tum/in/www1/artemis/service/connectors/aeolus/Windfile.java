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
        windfile.setId(aeolusDefinition.getMetadata().getId());
        List<Action> actions = new ArrayList<>();
        for (SerializedAction action : aeolusDefinition.getActions()) {
            if (action.getScript() != null) {
                actions.add(ScriptAction.deserialize(action));
            }
            else {
                actions.add(PlatformAction.deserialize(action));
            }
        }

        windfile.setActions(actions);
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

    public void setId(String id) {
        if (this.metadata == null) {
            this.metadata = new Metadata();
        }
        this.metadata.setId(id);
    }
}
