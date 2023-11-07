package de.tum.in.www1.artemis.service.connectors.aeolus;

/**
 * Represents a repository that can be used in a {@link Windfile}
 */
public class PlatformAction extends Action {

    private String kind;

    private String type;

    public PlatformAction() {
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public static PlatformAction deserialize(SerializedAction serializedAction) {
        PlatformAction platformAction = new PlatformAction();
        platformAction.setName(serializedAction.getName());
        platformAction.setKind(serializedAction.getKind());
        platformAction.setType(serializedAction.getType());
        platformAction.setParameters(serializedAction.getParameters());
        platformAction.setEnvironment(serializedAction.getEnvironment());
        platformAction.setRunAlways(serializedAction.isRunAlways());
        return platformAction;
    }
}
