package de.tum.in.www1.artemis.web.rest.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserPageableSearchDTO extends PageableSearchDTO<String> {

    /**
     * Set of authorities users need to match.
     */
    private Set<String> authorities;

    /**
     * Set of origins users need to match.
     */
    private Set<String> origins;

    /**
     * Set of status users need to match.
     */
    private Set<String> status;

    /**
     * Set of courseIds users need to be part in.
     */
    private Set<Long> courseIds;

    public Set<String> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(Set<String> authorities) {
        this.authorities = authorities;
    }

    public Set<String> getOrigins() {
        return origins;
    }

    public void setOrigins(Set<String> origins) {
        this.origins = origins;
    }

    public Set<String> getStatus() {
        return status;
    }

    public void setStatus(Set<String> status) {
        this.status = status;
    }

    public Set<Long> getCourseIds() {
        return courseIds;
    }

    public void setCourseIds(Set<Long> courseIds) {
        this.courseIds = courseIds;
    }
}
