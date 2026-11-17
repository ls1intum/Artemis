package de.tum.cit.aet.artemis.atlas.dto.practice;

import java.util.List;

import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerFromLiveClientDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PracticeSubmissionDTO(List<@Valid SubmittedAnswerFromLiveClientDTO> answers) {
}
