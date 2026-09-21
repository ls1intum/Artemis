package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Language;

/**
 * The text submission a team member sends over the team sync topic while they work on it.
 *
 * @param id        the id of the submission being edited, or {@code null} for the team's first submission
 * @param text      the text the member wrote
 * @param language  the language the client detected for that text
 * @param submitted whether the member submitted rather than auto-saved
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamTextSubmissionUpdateDTO(@Nullable Long id, @Nullable String text, @Nullable Language language, @Nullable Boolean submitted) implements Serializable {
}
