package de.tum.cit.aet.artemis.quiz.dto;

import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;

public record QuizTrainingAnswerDTO(SubmittedAnswer submittedAnswer) {

    public SubmittedAnswer getSubmittedAnswer() {
        return submittedAnswer;
    }
}
