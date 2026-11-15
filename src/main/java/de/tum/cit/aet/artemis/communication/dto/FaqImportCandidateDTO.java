package de.tum.cit.aet.artemis.communication.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.FaqState;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A visible FAQ and its source course, without loading the course entity for search results.
 *
 * @param id             source FAQ ID to submit when importing
 * @param questionTitle  source question
 * @param questionAnswer source answer shown in the preview, omitted when empty
 * @param faqState       source publication state, retained when copying
 * @param courseId       source course ID
 * @param courseTitle    source course name displayed beside the question, omitted when empty
 * @param courseSemester optional source semester, omitted when empty
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FaqImportCandidateDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String questionTitle,
        @Nullable String questionAnswer, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FaqState faqState, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long courseId,
        @Nullable String courseTitle, @Nullable String courseSemester) {
}
