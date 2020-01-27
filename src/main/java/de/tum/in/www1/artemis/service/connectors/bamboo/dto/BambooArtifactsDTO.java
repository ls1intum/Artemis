package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.net.URL;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BambooArtifactsDTO {

    private int size;

    @JsonProperty("artifact")
    private List<BambooArtifactDTO> artifacts;

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<BambooArtifactDTO> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<BambooArtifactDTO> artifacts) {
        this.artifacts = artifacts;
    }

    public static final class BambooArtifactDTO {

        private String name;

        private String producerJobKey;

        private boolean shared;

        private BambooArtifactLinkDTO link;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getProducerJobKey() {
            return producerJobKey;
        }

        public void setProducerJobKey(String producerJobKey) {
            this.producerJobKey = producerJobKey;
        }

        public boolean isShared() {
            return shared;
        }

        public void setShared(boolean shared) {
            this.shared = shared;
        }

        public BambooArtifactLinkDTO getLink() {
            return link;
        }

        public void setLink(BambooArtifactLinkDTO link) {
            this.link = link;
        }
    }

    public static final class BambooArtifactLinkDTO {

        @JsonProperty("href")
        private URL linkToArtifact;

        private String rel;

        public URL getLinkToArtifact() {
            return linkToArtifact;
        }

        public void setLinkToArtifact(URL linkToArtifact) {
            this.linkToArtifact = linkToArtifact;
        }

        public String getRel() {
            return rel;
        }

        public void setRel(String rel) {
            this.rel = rel;
        }
    }
}
