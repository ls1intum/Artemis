package de.tum.in.www1.artemis.service.connectors.aeolus;

/**
 * Represents a repository that can be used in a {@link Windfile}
 */
public class AeolusRepository {

    private String url;

    private String branch;

    private String path;

    /**
     * Creates a new AeolusRepository
     *
     * @param url    the url of the repository
     * @param branch the branch of the repository
     * @param path   the path of the repository
     */
    public AeolusRepository(String url, String branch, String path) {
        this.url = url;
        this.branch = branch;
        this.path = path;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
