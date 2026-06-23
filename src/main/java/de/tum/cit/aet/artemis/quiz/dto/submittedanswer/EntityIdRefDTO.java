package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Lightweight reference DTO that captures only the database id from a nested entity-shaped JSON object.
 * <p>
 * Used by the {@code FromLiveClient} family of submission DTOs to accept the rich entity-shaped JSON the
 * live, exam, and training quiz clients still send (e.g. a full {@code AnswerOption} or {@code DragItem}
 * payload) while only retaining the id needed to look up the server-managed instance.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record EntityIdRefDTO(Long id) {
}
