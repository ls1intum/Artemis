package de.tum.in.www1.artemis.service.connectors.ci.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CommitDTO(String hash, String repositorySlug, String branchName) {
}
