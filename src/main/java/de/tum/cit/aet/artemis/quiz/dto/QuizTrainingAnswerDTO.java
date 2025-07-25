package de.tum.cit.aet.artemis.quiz.dto;

import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;

public record QuizTrainingAnswerDTO(Long id, Double scoreInPoints, SubmittedAnswer submittedAnswer) {

    public SubmittedAnswer getSubmittedAnswer() {
        return submittedAnswer;
    }
}
