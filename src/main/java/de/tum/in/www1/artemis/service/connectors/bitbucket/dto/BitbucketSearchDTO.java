package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketSearchDTO {

    private int size;

    @JsonProperty("values")
    private List<Object> searchResults;

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<Object> getSearchResults() {
        return searchResults;
    }

    public void setSearchResults(List<Object> searchResults) {
        this.searchResults = searchResults;
    }
}
