package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.DragAndDropMappingReEvaluateDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropSubmittedAnswerFromStudentDTO(@NotNull Long questionId, @NotNull List<@Valid DragAndDropMappingReEvaluateDTO> mappings)
        implements SubmittedAnswerFromStudentDTO {
}
