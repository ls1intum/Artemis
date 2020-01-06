package de.tum.in.www1.artemis.service.connectors.jenkins.dto;

public class CommitDTO {

    private String hash;

    private String repositorySlug;

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getRepositorySlug() {
        return repositorySlug;
    }

    public void setRepositorySlug(String repositorySlug) {
        this.repositorySlug = repositorySlug;
    }
}
