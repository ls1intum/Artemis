package de.tum.cit.aet.artemis.core.dto.pageablesearch;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

// TODO: convert to Record, use composition for common attributes
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserPageableSearchDTO extends SearchTermPageableSearchDTO<String> {

    /**
     * Set of authorities users need to match.
     */
    private Set<String> authorities = new HashSet<>();

    /**
     * Set of origins users need to match.
     */
    private Set<String> origins = new HashSet<>();

    /**
     * Set of status users need to match.
     */
    private Set<String> status = new HashSet<>();

    private boolean findWithoutCourseEnrollment = false;

    /**
     * Set of registrationNumbers users need to match
     */
    private Set<String> registrationNumbers;

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

    public Set<String> getRegistrationNumbers() {
        return registrationNumbers;
    }

    public void setRegistrationNumbers(Set<String> registrationNumbers) {
        this.registrationNumbers = registrationNumbers;
    }

    public boolean isFindWithoutCourseEnrollment() {
        return findWithoutCourseEnrollment;
    }

    public void setFindWithoutCourseEnrollment(boolean findWithoutCourseEnrollment) {
        this.findWithoutCourseEnrollment = findWithoutCourseEnrollment;
    }
}
