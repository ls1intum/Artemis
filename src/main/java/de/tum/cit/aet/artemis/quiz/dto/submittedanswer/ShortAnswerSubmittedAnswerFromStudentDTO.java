package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSubmittedAnswerFromStudentDTO(@NotNull Long questionId, @NotNull List<@Valid ShortAnswerSubmittedTextFromStudentDTO> submittedTexts)
        implements SubmittedAnswerFromStudentDTO {

    public static ShortAnswerSubmittedAnswerFromStudentDTO of(ShortAnswerSubmittedAnswer submittedAnswer) {
        List<ShortAnswerSubmittedTextFromStudentDTO> submittedTexts = submittedAnswer.getSubmittedTexts().stream().map(ShortAnswerSubmittedTextFromStudentDTO::of).toList();
        return new ShortAnswerSubmittedAnswerFromStudentDTO(submittedAnswer.getQuizQuestion().getId(), submittedTexts);
    }
}
