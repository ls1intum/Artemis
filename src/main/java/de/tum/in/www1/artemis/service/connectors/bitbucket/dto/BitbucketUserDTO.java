package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketUserDTO {

    private String user;

    private Set<String> groups;

    public String getUser() {
        return user;
    }

    public void setUser(String username) {
        this.user = username;
    }

    public Set<String> getGroups() {
        return groups;
    }

    public void setGroups(Set<String> groups) {
        this.groups = groups;
    }

    /**
     * needed for Jackson
     */
    public BitbucketUserDTO() {
    }

    public BitbucketUserDTO(String username, Set<String> groups) {
        this.user = username;
        this.groups = groups;
    }
}
