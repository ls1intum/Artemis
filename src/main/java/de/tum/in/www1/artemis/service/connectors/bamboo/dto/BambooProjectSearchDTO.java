package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.util.List;

public class BambooProjectSearchDTO {

    private int size;

    private List<SearchResultDTO> searchResults;

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

    public static final class SearchResultDTO {

        private BambooProjectDTO searchEntity;

        public BambooProjectDTO getSearchEntity() {
            return searchEntity;
        }

        public void setSearchEntity(BambooProjectDTO searchEntity) {
            this.searchEntity = searchEntity;
        }
    }
}
