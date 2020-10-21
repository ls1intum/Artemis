package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketUserDTO {

    private String user;

    private Set<String> group;

    public String getUser() {
        return user;
    }

    public void setUser(String username) {
        this.user = username;
    }

    public Set<String> getGroup() {
        return group;
    }

    public void setGroup(Set<String> group) {
        this.group = group;
    }

    public BitbucketUserDTO(String username, Set<String> group) {
        this.user = username;
        this.group = group;
    }
}
