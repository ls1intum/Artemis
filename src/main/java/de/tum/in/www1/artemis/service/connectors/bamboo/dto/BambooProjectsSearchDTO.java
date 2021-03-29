package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BambooProjectsSearchDTO {

    private int size;

    private List<SearchResultDTO> searchResults = new ArrayList<>();

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<SearchResultDTO> getSearchResults() {
        return searchResults;
    }

    public void setSearchResults(List<SearchResultDTO> searchResults) {
        this.searchResults = searchResults;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class SearchResultDTO {

        private BambooProjectSearchDTO searchEntity;

        public BambooProjectSearchDTO getSearchEntity() {
            return searchEntity;
        }

        public void setSearchEntity(BambooProjectSearchDTO searchEntity) {
            this.searchEntity = searchEntity;
        }
    }
}
