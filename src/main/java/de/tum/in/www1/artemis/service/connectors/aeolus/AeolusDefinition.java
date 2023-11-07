package de.tum.in.www1.artemis.service.connectors.aeolus;

import java.util.List;

/**
 * Represents the Aeolus definition file ({@link Windfile}) in a serializable and usable form
 * to make it easier to be used in the client.
 */
public class AeolusDefinition {

    private String api;

    private Metadata metadata;

    private List<SerializedAction> actions;

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

    public List<SerializedAction> getActions() {
        return actions;
    }

    public void setActions(List<SerializedAction> actions) {
        this.actions = actions;
    }
}
