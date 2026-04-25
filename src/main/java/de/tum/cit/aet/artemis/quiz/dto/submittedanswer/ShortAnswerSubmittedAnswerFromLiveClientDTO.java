package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import java.util.Set;

import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShortAnswerSubmittedAnswerFromLiveClientDTO(EntityIdRefDTO quizQuestion, Set<@Valid ShortAnswerSubmittedTextFromLiveClientDTO> submittedTexts)
        implements SubmittedAnswerFromLiveClientDTO {
}
