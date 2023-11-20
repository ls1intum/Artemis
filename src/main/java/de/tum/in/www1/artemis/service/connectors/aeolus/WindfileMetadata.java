package de.tum.in.www1.artemis.service.connectors.aeolus;

/**
 * Represents the metadata of a {@link Windfile}
 */
public class WindfileMetadata {

    private String name;

    private String id;

    private String description;

    private String author;

    private String gitCredentials;

    private DockerConfig docker;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGitCredentials() {
        return gitCredentials;
    }

    public void setGitCredentials(String gitCredentials) {
        this.gitCredentials = gitCredentials;
    }

    public DockerConfig getDocker() {
        return docker;
    }

    public void setDocker(DockerConfig docker) {
        this.docker = docker;
    }
}
