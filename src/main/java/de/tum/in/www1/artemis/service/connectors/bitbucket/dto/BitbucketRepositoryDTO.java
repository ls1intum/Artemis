package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BitbucketRepositoryDTO(String id, String name, String slug, BitbucketProjectDTO project, String scmId, String state, String statusMessage, boolean forkable,
        LinksDTO links, String defaultBranch) {

    public BitbucketRepositoryDTO(String name, String defaultBranch) {
        this(null, name, null, null, null, null, null, false, null, defaultBranch);
    }

    public BitbucketRepositoryDTO(String id, String slug, String projectKey, String cloneSshUrl) {
        this(id, null, slug, new BitbucketProjectDTO(projectKey, null), null, null, null, false, new LinksDTO(), null);
        this.links.clones.add(new CloneDTO(cloneSshUrl, "ssh"));
    }

    /**
     * helper method
     * @return the clone url stored in the link based on ssh href
     */
    public String getCloneSshUrl() {
        if (this.links == null) {
            return null;
        }

        for (var clone : this.links.clones()) {
            if ("ssh".equals(clone.name())) {
                return clone.href();
            }
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LinksDTO(@JsonProperty("clone") List<CloneDTO> clones, List<CloneDTO> self) {

        public LinksDTO() {
            this(new ArrayList<>(), new ArrayList<>());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CloneDTO(String href, String name) {
    }
}
