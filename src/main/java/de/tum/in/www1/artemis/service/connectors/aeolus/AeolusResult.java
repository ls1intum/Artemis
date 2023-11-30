package de.tum.in.www1.artemis.service.connectors.aeolus;

public class AeolusResult {

    private String name;

    private String path;

    private String ignore;

    private String type;

    public AeolusResult(String name, String path, String ignore, String type) {
        this.name = name;
        this.path = path;
        this.ignore = ignore;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getIgnore() {
        return ignore;
    }

    public void setIgnore(String ignore) {
        this.ignore = ignore;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
