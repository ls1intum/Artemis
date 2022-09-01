package de.tum.in.www1.artemis.service.connectors.jenkins.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CommitDTO {

    private String hash;

    private String repositorySlug;

    private String branchName;

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

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }
}
