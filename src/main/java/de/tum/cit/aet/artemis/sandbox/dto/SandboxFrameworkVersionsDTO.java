package de.tum.cit.aet.artemis.sandbox.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * The Ares versions the sandbox can pin.
 *
 * @param selectableVersions the versions an instructor can pin, newest configured version first
 * @param templateVersions   the version the tests repository template of each supported project type pins, i.e. the version a new exercise starts with
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SandboxFrameworkVersionsDTO(List<String> selectableVersions, Map<ProjectType, String> templateVersions) {
}
