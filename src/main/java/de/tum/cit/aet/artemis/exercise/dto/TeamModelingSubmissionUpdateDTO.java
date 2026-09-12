package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The modeling submission a team member sends over the team sync topic while they work on it.
 *
 * @param id              the id of the submission being edited, or {@code null} for the team's first submission
 * @param model           the serialized diagram
 * @param explanationText the explanation the member wrote for the diagram
 * @param submitted       whether the member submitted rather than auto-saved
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamModelingSubmissionUpdateDTO(@Nullable Long id, @Nullable String model, @Nullable String explanationText, @Nullable Boolean submitted) implements Serializable {
}
