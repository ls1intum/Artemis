package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BitbucketUserDTO(String user, Set<String> groups) {
}
