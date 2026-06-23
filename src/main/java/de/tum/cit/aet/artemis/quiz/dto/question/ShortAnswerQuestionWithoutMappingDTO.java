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
        // Spots and solutions can be null on a question that has been filtered for students (filterForStudentsDuringQuiz
        // sets solutions to null to hide them); treat that as an empty list for the response shape.
        var spots = question.getSpots() == null ? List.<ShortAnswerSpotDTO>of() : question.getSpots().stream().map(ShortAnswerSpotDTO::of).toList();
        var solutions = question.getSolutions() == null ? List.<ShortAnswerSolutionDTO>of() : question.getSolutions().stream().map(ShortAnswerSolutionDTO::of).toList();
        // Use null for empty lists to match JSON round-trip behavior with @JsonInclude(NON_EMPTY)
        return new ShortAnswerQuestionWithoutMappingDTO(spots.isEmpty() ? null : spots, solutions.isEmpty() ? null : solutions, question.getSimilarityValue(),
                question.getMatchLetterCase());
    }

}
