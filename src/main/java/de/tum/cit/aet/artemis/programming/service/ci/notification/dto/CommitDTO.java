package de.tum.cit.aet.artemis.programming.service.ci.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CommitDTO(String hash, String repositorySlug, String branchName) {
}
