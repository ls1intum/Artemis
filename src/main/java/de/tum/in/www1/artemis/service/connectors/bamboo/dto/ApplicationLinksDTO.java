package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ApplicationLinksDTO {

    private List<ApplicationLinkDTO> applicationLinks;

    public List<ApplicationLinkDTO> getApplicationLinks() {
        return applicationLinks;
    }

    public void setApplicationLinks(List<ApplicationLinkDTO> applicationLinks) {
        this.applicationLinks = applicationLinks;
    }

    public static class ApplicationLinkDTO {

        private String id;

        private String name;

        private String description;

        private String type;

        private String rpcUrl;

        private String displayUrl;

        private boolean primary;

        private boolean system;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getRpcUrl() {
            return rpcUrl;
        }

        public void setRpcUrl(String rpcUrl) {
            this.rpcUrl = rpcUrl;
        }

        public String getDisplayUrl() {
            return displayUrl;
        }

        public void setDisplayUrl(String displayUrl) {
            this.displayUrl = displayUrl;
        }

        public boolean isPrimary() {
            return primary;
        }

        public void setPrimary(boolean primary) {
            this.primary = primary;
        }

        public boolean isSystem() {
            return system;
        }

        public void setSystem(boolean system) {
            this.system = system;
        }
    }
}
