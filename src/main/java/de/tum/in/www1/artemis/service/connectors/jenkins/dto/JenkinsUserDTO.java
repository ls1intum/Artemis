package de.tum.in.www1.artemis.service.connectors.jenkins.dto;

import java.util.List;

public class JenkinsUserDTO {

    public String _class;

    public String absoluteUrl;

    public Object description;

    public String fullName;

    public String id;

    public List<Property> property;

    public String getAbsoluteUrl() {
        return absoluteUrl;
    }

    public void setAbsoluteUrl(String absoluteUrl) {
        this.absoluteUrl = absoluteUrl;
    }

    public List<Property> getProperty() {
        return property;
    }

    public Object getDescription() {
        return description;
    }

    public void setDescription(Object description) {
        this.description = description;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public static class Property {

        public String _class;

        public List<Object> triggers;

        public boolean insensitiveSearch;

        public String address;
    }
}
