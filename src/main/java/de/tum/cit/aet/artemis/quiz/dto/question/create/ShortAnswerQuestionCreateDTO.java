package de.tum.cit.aet.artemis.quiz.dto.question.create;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerQuestionCreateDTO(@NotEmpty String title, String text, String hint, String explanation, double points, ScoringType scoringType, boolean randomizeOrder,
        List<ShortAnswerSpotCreateDTO> spots, List<ShortAnswerSolutionCreateDTO> solutions, List<ShortAnswerMappingCreateDTO> correctMappings, int similarityValue,
        boolean matchLetterCase) implements QuizQuestionCreateDTO {

    /**
     * Converts this DTO to a {@link ShortAnswerQuestion} domain object.
     * <p>
     * Maps the DTO properties to the corresponding fields in the domain object and transforms the lists
     * of {@link ShortAnswerSpotCreateDTO}, {@link ShortAnswerSolutionCreateDTO}, and {@link ShortAnswerMappingCreateDTO}
     * into lists of {@link ShortAnswerSpot}, {@link ShortAnswerSolution}, and {@link ShortAnswerMapping} objects
     * by invoking their respective {@code toDomainObject} methods.
     *
     * @return the {@link ShortAnswerQuestion} domain object with properties and child entities set from this DTO
     */
    public ShortAnswerQuestion toDomainObject() {
        ShortAnswerQuestion shortAnswerQuestion = new ShortAnswerQuestion();
        shortAnswerQuestion.setTitle(title);
        shortAnswerQuestion.setText(text);
        shortAnswerQuestion.setHint(hint);
        shortAnswerQuestion.setExplanation(explanation);
        shortAnswerQuestion.setPoints(points);
        shortAnswerQuestion.setScoringType(scoringType);
        shortAnswerQuestion.setRandomizeOrder(randomizeOrder);
        shortAnswerQuestion.setSimilarityValue(similarityValue);
        shortAnswerQuestion.setMatchLetterCase(matchLetterCase);

        List<ShortAnswerSpot> shortAnswerSpots = spots.stream().map(ShortAnswerSpotCreateDTO::toDomainObject).toList();
        List<ShortAnswerSolution> shortAnswerSolutions = solutions.stream().map(ShortAnswerSolutionCreateDTO::toDomainObject).toList();
        List<ShortAnswerMapping> shortAnswerMappings = correctMappings.stream().map(ShortAnswerMappingCreateDTO::toDomainObject).toList();
        shortAnswerQuestion.setSpots(shortAnswerSpots);
        shortAnswerQuestion.setSolutions(shortAnswerSolutions);
        shortAnswerQuestion.setCorrectMappings(shortAnswerMappings);
        return shortAnswerQuestion;
    }
}
