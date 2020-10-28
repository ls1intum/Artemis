package de.tum.in.www1.artemis.service.connectors.jira.dto;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraUserDTO {

    private String key;

    private String name;

    private String emailAddress;

    private String displayName;

    private JiraUserGroupsDTO groups;

    private List<String> applicationKeys = new ArrayList<>();

    /**
     * needed for Jackson
     */
    public JiraUserDTO() {
    }

    public JiraUserDTO(String name) {
        this.name = name;
    }

    public JiraUserDTO(String name, String displayName, String emailAddress, JiraUserGroupsDTO groups) {
        this.name = name;
        this.displayName = displayName;
        this.emailAddress = emailAddress;
        this.groups = groups;
    }

    public JiraUserDTO(String key, String name, String displayName, String emailAddress, List<String> applicationKeys) {
        this.key = key;
        this.name = name;
        this.displayName = displayName;
        this.emailAddress = emailAddress;
        this.applicationKeys = applicationKeys;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

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

    public List<String> getApplicationKeys() {
        return applicationKeys;
    }

    public void setApplicationKeys(List<String> applicationKeys) {
        this.applicationKeys = applicationKeys;
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

        /**
         * needed for Jackson
         */
        public JiraUserGroupDTO() {
        }

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
