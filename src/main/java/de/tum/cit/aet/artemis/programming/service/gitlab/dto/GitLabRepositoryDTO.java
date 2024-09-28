package de.tum.cit.aet.artemis.programming.service.gitlab.dto;

import java.net.URL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

// Gitlab support will be removed in 8.0.0. Please migrate to LocalVC using e.g. the PR https://github.com/ls1intum/Artemis/pull/8972
@Deprecated(since = "7.5.0", forRemoval = true)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GitLabRepositoryDTO(String name, String url, String description, URL homepage) {
}
