package de.tum.cit.aet.artemis.assessment.dto;

import java.util.List;

import de.tum.cit.aet.artemis.core.dto.pageablesearch.PageableSearchDTO;

public class FeedbackPageableDTO extends PageableSearchDTO<String> {

    private List<String> filterTasks;

    private List<String> filterTestCases;

    private String[] filterOccurrence;

    private String searchTerm;

    public List<String> getFilterTasks() {
        return filterTasks;
    }

    public void setFilterTasks(List<String> filterTasks) {
        this.filterTasks = filterTasks;
    }

    public List<String> getFilterTestCases() {
        return filterTestCases;
    }

    public void setFilterTestCases(List<String> filterTestCases) {
        this.filterTestCases = filterTestCases;
    }

    public String[] getFilterOccurrence() {
        return filterOccurrence;
    }

    public void setFilterOccurrence(String[] filterOccurrence) {
        this.filterOccurrence = filterOccurrence;
    }

    public String getSearchTerm() {
        return searchTerm != null ? searchTerm : "";
    }

    public void setSearchTerm(String searchTerm) {
        this.searchTerm = searchTerm;
    }
}
