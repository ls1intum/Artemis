package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The files of the template repository, from which instructors pick paths and scripts for a security policy.
 *
 * @param paths     the paths of the regular files relative to the repository root, sorted
 * @param truncated whether the repository has more files than are listed
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SecurityTemplateFilesDTO(List<String> paths, boolean truncated) {
}
