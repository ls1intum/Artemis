package de.tum.cit.aet.artemis.programming.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkAction;

/**
 * The Security Framework setting an instructor chooses while creating or importing a programming exercise. It is applied to the tests repository before the
 * first builds run, so the exercise is built once with the chosen setting.
 *
 * @param action           whether the sandbox is switched on or off; required, a choice without it is rejected
 * @param frameworkVersion the Ares version to pin; absent to keep the version the repository pins
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SecurityFrameworkChoiceDTO(@Nullable SecurityFrameworkAction action, @Nullable String frameworkVersion) {
}
