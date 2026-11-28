package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Where one git repository of a programming exercise is.
 *
 * @param type     {@code template}, {@code solution}, {@code tests} or {@code auxiliary}
 * @param name     the name of an auxiliary repository, which tells several of them apart; absent for the others
 * @param cloneUrl the HTTPS address to clone from, without credentials
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseRepositoryLocationDTO(String type, String name, String cloneUrl) {
}
