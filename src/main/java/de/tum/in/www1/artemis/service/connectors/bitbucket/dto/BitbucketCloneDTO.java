package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BitbucketCloneDTO {

    private String name;

    @JsonProperty("project")
    private CloneDetailsDTO cloneDetails;

    public BitbucketCloneDTO(String name, CloneDetailsDTO cloneDetails) {
        this.name = name;
        this.cloneDetails = cloneDetails;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CloneDetailsDTO getCloneDetails() {
        return cloneDetails;
    }

    public void setCloneDetails(CloneDetailsDTO cloneDetails) {
        this.cloneDetails = cloneDetails;
    }

    /**
     * Creating a description for the console log
     * @return description with name and project key
     */
    public String toString() {
        String description = String.format("name=%s", name);
        if (cloneDetails != null) {
            description = description + "," + cloneDetails.toString();
        }
        return description;
    }

    public static final class CloneDetailsDTO {

        private String key;

        public CloneDetailsDTO(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String toString() {
            return String.format("project={key=%s}", key);
        }
    }
}
