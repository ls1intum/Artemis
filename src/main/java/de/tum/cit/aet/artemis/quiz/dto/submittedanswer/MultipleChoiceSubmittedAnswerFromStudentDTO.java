package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;

@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "multiple-choice" }, defaultValue = "multiple-choice"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MultipleChoiceSubmittedAnswerFromStudentDTO(@NotNull Long questionId, @NotNull Set<Long> selectedOptions) implements SubmittedAnswerFromStudentDTO {

    public static MultipleChoiceSubmittedAnswerFromStudentDTO of(MultipleChoiceSubmittedAnswer submittedAnswer) {
        Set<Long> selectedOptionIds = submittedAnswer.toSelectedIds();
        return new MultipleChoiceSubmittedAnswerFromStudentDTO(submittedAnswer.getQuizQuestion().getId(), selectedOptionIds);
    }
}
