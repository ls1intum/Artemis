package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;

public class BitbucketProjectSearchDTO {

    private int size;

    @JsonProperty("values")
    private List<BitbucketProjectDTO> searchResults;

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<BitbucketProjectDTO> getSearchResults() {
        return searchResults;
    }

    public void setSearchResults(List<BitbucketProjectDTO> searchResults) {
        this.searchResults = searchResults;
    }
}
