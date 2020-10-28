package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import java.util.ArrayList;
import java.util.List;

public class BitbucketRepositoryDTO {

    private String id;

    private String name;

    private String slug;

    private BitbucketProjectDTO project;

    private String scmId;

    private String state;

    private String statusMessage;

    private boolean forkable;

    private LinksDTO links;

    /**
     * needed for Jackson
     */
    public BitbucketRepositoryDTO() {
    }

    public BitbucketRepositoryDTO(String name) {
        this.name = name;
    }

    public BitbucketRepositoryDTO(String name, String projectKey) {
        this.name = name;
        this.project = new BitbucketProjectDTO(projectKey);
    }

    public BitbucketRepositoryDTO(String id, String slug, String projectKey, String cloneSshUrl) {
        this.id = id;
        this.slug = slug;
        this.project = new BitbucketProjectDTO(projectKey);
        this.links = new LinksDTO();
        this.links.clone.add(new LinksDTO.CloneDTO(cloneSshUrl, "ssh"));
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

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public BitbucketProjectDTO getProject() {
        return project;
    }

    public void setProject(BitbucketProjectDTO project) {
        this.project = project;
    }

    public String getScmId() {
        return scmId;
    }

    public void setScmId(String scmId) {
        this.scmId = scmId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public boolean forkable() {
        return forkable;
    }

    public void setForkable(boolean forkable) {
        this.forkable = forkable;
    }

    public LinksDTO getLinks() {
        return links;
    }

    public void setLinks(LinksDTO links) {
        this.links = links;
    }

    /**
     * helper method
     * @return the clone url stored in the link based on http href
     */
    public String getCloneUrl() {
        if (this.links == null) {
            return null;
        }

        for (var clone : this.links.clone) {
            if ("http".equals(clone.getName())) {
                return clone.getHref();
            }
        }
        return null;
    }

    /**
     * helper method
     * @return the clone url stored in the link based on ssh href
     */
    public String getCloneSshUrl() {
        if (this.links == null) {
            return null;
        }

        for (var clone : this.links.clone) {
            if ("ssh".equals(clone.getName())) {
                return clone.getHref();
            }
        }
        return null;
    }

    public static class LinksDTO {

        private List<CloneDTO> clone = new ArrayList<>();

        private List<CloneDTO> self = new ArrayList<>();

        /**
         * needed for Jackson
         */
        public LinksDTO() {
        }

        public List<CloneDTO> getClone() {
            return clone;
        }

        public void setClone(List<CloneDTO> clone) {
            this.clone = clone;
        }

        public List<CloneDTO> getSelf() {
            return self;
        }

        public void setSelf(List<CloneDTO> self) {
            this.self = self;
        }

        public static class CloneDTO {

            private String href;

            private String name;

            /**
             * empty constructor needed for Jackson
             */
            public CloneDTO() {
            }

            public CloneDTO(String href, String name) {
                this.href = href;
                this.name = name;
            }

            public String getHref() {
                return href;
            }

            public void setHref(String href) {
                this.href = href;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
        }
    }
}
