package de.tum.cit.aet.artemis.quiz.dto.question.create;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
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
        // Generate stable tempIDs: use real id if persisted, otherwise generate a unique one.
        long tempIdCounter = 1;
        Map<ShortAnswerSpot, Long> spotTempIds = new HashMap<>();
        for (ShortAnswerSpot spot : question.getSpots()) {
            spotTempIds.put(spot, spot.getId() != null ? spot.getId() : tempIdCounter++);
        }
        Map<ShortAnswerSolution, Long> solutionTempIds = new HashMap<>();
        for (ShortAnswerSolution sol : question.getSolutions()) {
            solutionTempIds.put(sol, sol.getId() != null ? sol.getId() : tempIdCounter++);
        }

        List<ShortAnswerSpotCreateDTO> spotDTOs = question.getSpots().stream().map(s -> new ShortAnswerSpotCreateDTO(spotTempIds.get(s), s.getSpotNr(), s.getWidth())).toList();
        List<ShortAnswerSolutionCreateDTO> solutionDTOs = question.getSolutions().stream().map(s -> new ShortAnswerSolutionCreateDTO(solutionTempIds.get(s), s.getText())).toList();
        List<ShortAnswerMappingCreateDTO> mappingDTOs = question.getCorrectMappings().stream()
                .map(m -> new ShortAnswerMappingCreateDTO(solutionTempIds.get(m.getSolution()), spotTempIds.get(m.getSpot()))).toList();

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
        ShortAnswerQuestion question = new ShortAnswerQuestion();
        question.setTitle(title);
        question.setText(text);
        question.setHint(hint);
        question.setExplanation(explanation);
        question.setPoints(points);
        question.setScoringType(scoringType);
        question.setRandomizeOrder(randomizeOrder != null ? randomizeOrder : Boolean.FALSE);
        question.setSimilarityValue(similarityValue);
        question.setMatchLetterCase(matchLetterCase);

        List<ShortAnswerSpot> spotEntities = spots.stream().map(ShortAnswerSpotCreateDTO::toDomainObject).toList();
        List<ShortAnswerSolution> solutionEntities = solutions.stream().map(ShortAnswerSolutionCreateDTO::toDomainObject).toList();
        question.setSpots(spotEntities);
        question.setSolutions(solutionEntities);

        // Resolve mappings using DTO tempIDs to connect to the created domain objects.
        Map<Long, ShortAnswerSpot> tempToSpot = new HashMap<>();
        for (int i = 0; i < spots.size(); i++) {
            tempToSpot.put(spots.get(i).tempID(), spotEntities.get(i));
        }
        Map<Long, ShortAnswerSolution> tempToSolution = new HashMap<>();
        for (int i = 0; i < solutions.size(); i++) {
            tempToSolution.put(solutions.get(i).tempID(), solutionEntities.get(i));
        }

        List<ShortAnswerMapping> mappings = correctMappings.stream().map(m -> {
            ShortAnswerSpot spot = tempToSpot.get(m.spotTempId());
            ShortAnswerSolution solution = tempToSolution.get(m.solutionTempId());
            if (spot == null || solution == null) {
                throw new BadRequestAlertException("Could not resolve short answer mappings", "quizExercise", "invalidMappings");
            }
            ShortAnswerMapping mapping = new ShortAnswerMapping();
            mapping.setSpot(spot);
            mapping.setSolution(solution);
            return mapping;
        }).toList();
        question.setCorrectMappings(mappings);
        return question;
    }
}
