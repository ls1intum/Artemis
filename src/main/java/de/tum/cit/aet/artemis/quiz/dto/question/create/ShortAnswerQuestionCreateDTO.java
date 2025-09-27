package de.tum.cit.aet.artemis.quiz.dto.question.create;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerQuestionCreateDTO(@NotEmpty String title, String text, String hint, String explanation, @NotNull @Positive Double points, @NotNull ScoringType scoringType,
        Boolean randomizeOrder, @NotEmpty List<@Valid ShortAnswerSpotCreateDTO> spots, @NotEmpty List<@Valid ShortAnswerSolutionCreateDTO> solutions,
        @NotEmpty List<@Valid ShortAnswerMappingCreateDTO> correctMappings, @NotNull Integer similarityValue, @NotNull Boolean matchLetterCase) implements QuizQuestionCreateDTO {

    /**
     * Creates a {@link ShortAnswerQuestionCreateDTO} from the given {@link ShortAnswerQuestion} domain object.
     * <p>
     * Maps the domain object's properties to the corresponding DTO fields and transforms the lists
     * of {@link ShortAnswerSpot}, {@link ShortAnswerSolution}, and {@link ShortAnswerMapping} into lists
     * of {@link ShortAnswerSpotCreateDTO}, {@link ShortAnswerSolutionCreateDTO}, and {@link ShortAnswerMappingCreateDTO}
     * by invoking their respective {@code of} methods.
     *
     * @param question the {@link ShortAnswerQuestion} domain object to convert
     * @return the {@link ShortAnswerQuestionCreateDTO} with properties and child DTOs set from the domain object
     */
    public static ShortAnswerQuestionCreateDTO of(ShortAnswerQuestion question) {
        List<ShortAnswerSpotCreateDTO> spotDTOs = question.getSpots().stream().map(ShortAnswerSpotCreateDTO::of).toList();
        List<ShortAnswerSolutionCreateDTO> solutionDTOs = question.getSolutions().stream().map(ShortAnswerSolutionCreateDTO::of).toList();
        List<ShortAnswerMappingCreateDTO> mappingDTOs = question.getCorrectMappings().stream().map(ShortAnswerMappingCreateDTO::of).toList();
        return new ShortAnswerQuestionCreateDTO(question.getTitle(), question.getText(), question.getHint(), question.getExplanation(), question.getPoints(),
                question.getScoringType(), question.isRandomizeOrder(), spotDTOs, solutionDTOs, mappingDTOs, question.getSimilarityValue(), question.getMatchLetterCase());
    }

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
        shortAnswerQuestion.setRandomizeOrder(randomizeOrder != null ? randomizeOrder : Boolean.FALSE);
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
