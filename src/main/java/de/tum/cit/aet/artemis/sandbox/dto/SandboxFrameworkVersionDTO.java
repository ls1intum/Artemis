package de.tum.cit.aet.artemis.sandbox.dto;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The Ares version an instructor asks to pin for the sandbox of an exercise.
 *
 * @param frameworkVersion one of the selectable versions, see {@link SandboxFrameworkVersionsDTO}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SandboxFrameworkVersionDTO(@NotBlank String frameworkVersion) {
}
