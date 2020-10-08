package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import java.util.List;

public class RemoteBitbucketRepositoryDTO {

    private String id;

    private String name;

    private String slug;

    private RemoteBitbucketProjectDTO project;

    private String scmId;

    private String state;

    private String statusMessage;

    private boolean forkable;

    private LinksDTO links;

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

    public RemoteBitbucketProjectDTO getProject() {
        return project;
    }

    public void setProject(RemoteBitbucketProjectDTO project) {
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

    public String getCloneUrl() {
        if (this.links == null)
            return null;

        for (var clone : this.links.clone) {
            if ("http".equals(clone.getName())) {
                return clone.getHref();
            }
        }
        return null;
    }

    public String getCloneSshUrl() {
        if (this.links == null)
            return null;

        for (var clone : this.links.clone) {
            if ("ssh".equals(clone.getName())) {
                return clone.getHref();
            }
        }
        return null;
    }

    public static class LinksDTO {

        private List<CloneDTO> clone;

        private List<CloneDTO> self;

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
