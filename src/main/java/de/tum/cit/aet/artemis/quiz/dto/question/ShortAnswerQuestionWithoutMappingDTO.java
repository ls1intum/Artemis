package de.tum.cit.aet.artemis.quiz.dto.question;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.dto.ShortAnswerSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.ShortAnswerSpotDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerQuestionWithoutMappingDTO(List<ShortAnswerSpotDTO> spots, List<ShortAnswerSolutionDTO> solutions, Integer similarityValue, Boolean matchLetterCase) {

    /**
     * Creates a DTO from the given question, normalizing empty lists to null for consistent JSON serialization.
     *
     * @param question the short answer question
     * @return the DTO
     */
    public static ShortAnswerQuestionWithoutMappingDTO of(ShortAnswerQuestion question) {
        var spots = question.getSpots() == null ? List.<ShortAnswerSpotDTO>of() : question.getSpots().stream().map(ShortAnswerSpotDTO::of).toList();
        var solutions = question.getSolutions() == null ? List.<ShortAnswerSolutionDTO>of() : question.getSolutions().stream().map(ShortAnswerSolutionDTO::of).toList();
        return new ShortAnswerQuestionWithoutMappingDTO(spots.isEmpty() ? null : spots, solutions.isEmpty() ? null : solutions, question.getSimilarityValue(),
                question.getMatchLetterCase());
    }

    /**
     * Creates the student-facing representation used before solutions may be published.
     *
     * @param question the short answer question
     * @return a DTO containing spots and scoring settings, but no solution texts
     */
    public static ShortAnswerQuestionWithoutMappingDTO withoutSolutions(ShortAnswerQuestion question) {
        var spots = question.getSpots() == null ? List.<ShortAnswerSpotDTO>of() : question.getSpots().stream().map(ShortAnswerSpotDTO::of).toList();
        return new ShortAnswerQuestionWithoutMappingDTO(spots.isEmpty() ? null : spots, null, question.getSimilarityValue(), question.getMatchLetterCase());
    }

}
