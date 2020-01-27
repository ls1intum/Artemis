package de.tum.in.www1.artemis.service.connectors.jira.dto;

import java.net.URL;
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

        private int size;

        private Set<JiraUserGroupDTO> items;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public Set<JiraUserGroupDTO> getItems() {
            return items;
        }

        public void setItems(Set<JiraUserGroupDTO> items) {
            this.items = items;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class JiraUserGroupDTO {

        private String name;

        private URL self;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public URL getSelf() {
            return self;
        }

        public void setSelf(URL self) {
            this.self = self;
        }
    }
}
