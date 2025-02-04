package de.tum.cit.aet.artemis.quiz.dto;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;

public record ShortAnswerSolutionDTO(Long id, String text, Boolean invalid) {

    public static ShortAnswerSolutionDTO of(ShortAnswerSolution solution) {
        return new ShortAnswerSolutionDTO(solution.getId(), solution.getText(), solution.isInvalid());
    }

}
