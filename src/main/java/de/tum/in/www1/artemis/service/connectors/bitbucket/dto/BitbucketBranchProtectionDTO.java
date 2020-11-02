package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BitbucketBranchProtectionDTO {

    @JsonProperty("type")
    private String protectionType;

    private MatcherDTO matcher;

    /**
     * needed for Jackson
     */
    public BitbucketBranchProtectionDTO() {
    }

    public BitbucketBranchProtectionDTO(String protectionType, MatcherDTO matcher) {
        this.protectionType = protectionType;
        this.matcher = matcher;
    }

    public String getProtectionType() {
        return protectionType;
    }

    public void setProtectionType(String protectionType) {
        this.protectionType = protectionType;
    }

    public MatcherDTO getMatcher() {
        return matcher;
    }

    public void setMatcher(MatcherDTO matcher) {
        this.matcher = matcher;
    }

    public static final class MatcherDTO {

        private String displayId;

        private String id;

        private TypeDTO type;

        private boolean active;

        /**
         * needed for Jackson
         */
        public MatcherDTO() {
        }

        public MatcherDTO(String displayId, String id, TypeDTO type, boolean active) {
            this.displayId = displayId;
            this.id = id;
            this.type = type;
            this.active = active;
        }

        public String getDisplayId() {
            return displayId;
        }

        public void setDisplayId(String displayId) {
            this.displayId = displayId;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public TypeDTO getType() {
            return type;
        }

        public void setType(TypeDTO type) {
            this.type = type;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }

    public static final class TypeDTO {

        private String id;

        private String name;

        /**
         * needed for Jackson
         */
        public TypeDTO() {
        }

        public TypeDTO(String id, String name) {
            this.id = id;
            this.name = name;
        }

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
    }
}
