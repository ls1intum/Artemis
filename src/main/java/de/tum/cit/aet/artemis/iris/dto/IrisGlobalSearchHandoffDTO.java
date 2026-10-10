package de.tum.cit.aet.artemis.iris.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request to continue a global search answer in the Iris chat of a course: the chat opens on the chosen lecture or exercise, with the question and the answer the
 * student saw already in it.
 *
 * @param courseId the course whose chat continues the answer
 * @param context  the lecture or exercise the chat starts on, or {@code null} for the course itself
 * @param question the question the student asked in global search
 * @param answer   the answer Iris gave, with its citations already in the chat's citation format
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisGlobalSearchHandoffDTO(long courseId, @Valid @Nullable IrisPendingContextDTO context, @NotBlank @Size(max = MAX_QUESTION_LENGTH) String question,
        @NotBlank @Size(max = MAX_ANSWER_LENGTH) String answer) {

    /** Far above any question typed into the search palette; bounds what a single request can store. */
    public static final int MAX_QUESTION_LENGTH = 2_000;

    /** Far above any answer the global search pipeline writes, citations included; bounds what a single request can store. */
    public static final int MAX_ANSWER_LENGTH = 20_000;
}
