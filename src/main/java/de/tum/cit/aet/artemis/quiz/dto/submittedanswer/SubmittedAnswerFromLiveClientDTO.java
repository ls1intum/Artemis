package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Polymorphic request DTO for the rich entity-shaped JSON that the live, exam, and training quiz clients
 * send. Each concrete subtype mirrors the JSON shape produced by the corresponding {@code SubmittedAnswer}
 * subclass on the client, but stores only the database ids needed to resolve server-managed entities — all
 * other fields the client serializes are silently ignored.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = MultipleChoiceSubmittedAnswerFromLiveClientDTO.class, name = "multiple-choice"),
        @JsonSubTypes.Type(value = DragAndDropSubmittedAnswerFromLiveClientDTO.class, name = "drag-and-drop"),
        @JsonSubTypes.Type(value = ShortAnswerSubmittedAnswerFromLiveClientDTO.class, name = "short-answer") })
public sealed interface SubmittedAnswerFromLiveClientDTO
        permits DragAndDropSubmittedAnswerFromLiveClientDTO, MultipleChoiceSubmittedAnswerFromLiveClientDTO, ShortAnswerSubmittedAnswerFromLiveClientDTO {

    EntityIdRefDTO quizQuestion();
}
