package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketDefaultBranchDTO {

    private String id;

    private String displayId;

    private String type;

    private String latestCommit;

    /**
     * needed for Jackson
     */
    public BitbucketDefaultBranchDTO() {
        // intentionally left empty
    }

    public BitbucketDefaultBranchDTO(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayId() {
        return displayId;
    }

    public void setDisplayId(String displayId) {
        this.displayId = displayId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLatestCommit() {
        return latestCommit;
    }

    public void setLatestCommit(String latestCommit) {
        this.latestCommit = latestCommit;
    }
}
