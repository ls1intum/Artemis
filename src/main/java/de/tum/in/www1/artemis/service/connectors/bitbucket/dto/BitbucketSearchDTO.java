package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketSearchDTO<T> {

    private int size;

    @JsonProperty("values")
    private List<T> searchResults;

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<T> getSearchResults() {
        return searchResults;
    }

    public void setSearchResults(List<T> searchResults) {
        this.searchResults = searchResults;
    }
}
