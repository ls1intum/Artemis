package de.tum.in.www1.artemis.service.connectors.gitlab.dto;

import java.net.URL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GitLabRepositoryDTO(String name, String url, String description, URL homepage) {
}
