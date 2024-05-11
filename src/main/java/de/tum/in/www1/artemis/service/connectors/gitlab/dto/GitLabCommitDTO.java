package de.tum.in.www1.artemis.service.connectors.gitlab.dto;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GitLabCommitDTO(@JsonProperty("id") String hash, String message, ZonedDateTime timestamp, @JsonProperty("url") String commitUrl, Author author, List<String> added,
        List<String> modified, List<String> removed) {

    // make sure the lists are never null
    public GitLabCommitDTO {
        added = (added != null) ? added : new ArrayList<>();
        modified = (modified != null) ? modified : new ArrayList<>();
        removed = (removed != null) ? removed : new ArrayList<>();
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Author(String name, String email) {
    }
}
