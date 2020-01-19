package de.tum.in.www1.artemis.service.connectors.jira.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraUserDTO {

    private String name;

    private String emailAddress;

    private String displayName;

    private JiraUserGroupsDTO groups;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public JiraUserGroupsDTO getGroups() {
        return groups;
    }

    public void setGroups(JiraUserGroupsDTO groups) {
        this.groups = groups;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class JiraUserGroupsDTO {

        private Set<String> items;

        public Set<String> getItems() {
            return items;
        }

        public void setItems(Set<String> items) {
            this.items = items;
        }
    }
}
