package de.tum.in.www1.artemis.service.connectors.aeolus;

/**
 * Represents a CI action that is intended to run only on a specific target, can be used in a {@link Windfile}.
 */
public class PlatformAction extends Action {

    private String kind;

    private String type;

    private String platform;

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

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }
}
