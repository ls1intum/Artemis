package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Extends the default pagable search class to include user management user filters.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserManagementPagableSearchDTO extends PageableSearchDTO<String> {

    /**
     * A list of users selected filters.
     */
    private List<UserManagementUserFilter> filters;

    public List<UserManagementUserFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<UserManagementUserFilter> filters) {
        this.filters = filters;
    }

    /**
     * The available filters for the user management user queries.
     */
    public enum UserManagementUserFilter {
        WITH_REG_NO, WITHOUT_REG_NO, INTERNAL, EXTERNAL;
    }
}
