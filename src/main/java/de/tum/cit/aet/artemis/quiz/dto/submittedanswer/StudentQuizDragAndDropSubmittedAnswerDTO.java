package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentQuizDragAndDropSubmittedAnswerDTO(@NotNull Long questionId, @NotNull Set<StudentQuizDragAndDropMappingDTO> mappings) implements StudentQuizSubmittedAnswerDTO {
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record StudentQuizDragAndDropMappingDTO(@NotNull Long dragItemId, @NotNull Long dropLocationId) {
}
