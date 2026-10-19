package de.tum.cit.aet.artemis.exercise.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Who a persisted submission belongs to, read as two scalars instead of loading the submission and its participation.
 * <p>
 * The ownership gate on the submission endpoints only needs to compare a login and, for team exercises, a team short
 * name. Loading the submission entity for that pulls in its participation, exercise, exercise group, exam and course
 * through eager associations, which is several statements for two strings.
 *
 * @param studentLogin  the login of the owning student, null for a team participation or when the submission has no
 *                          student participation
 * @param teamShortName the short name of the owning team, null for an individual participation
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionOwnerDTO(@Nullable String studentLogin, @Nullable String teamShortName) {
}
