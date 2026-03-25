package de.tum.cit.aet.artemis.quiz.dto.question.fromEditor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;

/**
 * DTO for short answer questions in the editor context.
 * Supports both creating new questions (id is null) and updating existing questions (id is non-null).
 *
 * @param id              the ID of the question, null for new questions
 * @param title           the title of the question
 * @param text            the question text
 * @param hint            the hint for the question
 * @param explanation     the explanation for the question
 * @param points          the points for the question
 * @param scoringType     the scoring type
 * @param randomizeOrder  whether to randomize order
 * @param spots           the list of spots
 * @param solutions       the list of solutions
 * @param correctMappings the list of correct mappings
 * @param similarityValue the similarity value for comparing answers
 * @param matchLetterCase whether to match letter case
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerQuestionFromEditorDTO(Long id, @NotEmpty String title, String text, String hint, String explanation, @NotNull @Positive Double points,
        @NotNull ScoringType scoringType, Boolean randomizeOrder, @NotEmpty List<@Valid ShortAnswerSpotFromEditorDTO> spots,
        @NotEmpty List<@Valid ShortAnswerSolutionFromEditorDTO> solutions, @NotEmpty List<@Valid ShortAnswerMappingFromEditorDTO> correctMappings, @NotNull Integer similarityValue,
        @NotNull Boolean matchLetterCase) implements QuizQuestionFromEditorDTO {

    /**
     * Creates a ShortAnswerQuestionFromEditorDTO from the given ShortAnswerQuestion domain object.
     *
     * @param question the question to convert
     * @return the corresponding DTO
     */
    public static ShortAnswerQuestionFromEditorDTO of(ShortAnswerQuestion question) {
        List<ShortAnswerSpotFromEditorDTO> spotDTOs = question.getSpots().stream().map(ShortAnswerSpotFromEditorDTO::of).toList();
        List<ShortAnswerSolutionFromEditorDTO> solutionDTOs = question.getSolutions().stream().map(ShortAnswerSolutionFromEditorDTO::of).toList();
        List<ShortAnswerMappingFromEditorDTO> mappingDTOs = question.getCorrectMappings().stream().map(ShortAnswerMappingFromEditorDTO::of).toList();
        return new ShortAnswerQuestionFromEditorDTO(question.getId(), question.getTitle(), question.getText(), question.getHint(), question.getExplanation(), question.getPoints(),
                question.getScoringType(), question.isRandomizeOrder(), spotDTOs, solutionDTOs, mappingDTOs, question.getSimilarityValue(), question.getMatchLetterCase());
    }

    /**
     * Creates a new ShortAnswerQuestion domain object from this DTO.
     *
     * @return a new ShortAnswerQuestion domain object
     */
    @Override
    public ShortAnswerQuestion toDomainObject() {
        ShortAnswerQuestion question = new ShortAnswerQuestion();
        question.setId(id);
        question.setTitle(title);
        question.setText(text);
        question.setHint(hint);
        question.setExplanation(explanation);
        question.setPoints(points);
        question.setScoringType(scoringType);
        question.setRandomizeOrder(randomizeOrder != null ? randomizeOrder : Boolean.FALSE);
        question.setSimilarityValue(similarityValue);
        question.setMatchLetterCase(matchLetterCase);

        List<ShortAnswerSpot> spotEntities = new ArrayList<>(spots.stream().map(ShortAnswerSpotFromEditorDTO::toDomainObject).toList());
        List<ShortAnswerSolution> solutionEntities = new ArrayList<>(solutions.stream().map(ShortAnswerSolutionFromEditorDTO::toDomainObject).toList());
        question.setSpots(spotEntities);
        question.setSolutions(solutionEntities);

        // Resolve mappings using DTO effectiveId (tempID for new items, id for existing).
        Map<Long, ShortAnswerSpot> effectiveIdToSpot = new HashMap<>();
        for (int i = 0; i < spots.size(); i++) {
            effectiveIdToSpot.put(spots.get(i).effectiveId(), spotEntities.get(i));
        }
        Map<Long, ShortAnswerSolution> effectiveIdToSolution = new HashMap<>();
        for (int i = 0; i < solutions.size(); i++) {
            effectiveIdToSolution.put(solutions.get(i).effectiveId(), solutionEntities.get(i));
        }

        List<ShortAnswerMapping> mappings = new ArrayList<>(correctMappings.stream().map(m -> {
            ShortAnswerSpot spot = effectiveIdToSpot.get(m.spotTempId());
            ShortAnswerSolution solution = effectiveIdToSolution.get(m.solutionTempId());
            if (spot == null || solution == null) {
                throw new BadRequestAlertException("Could not resolve short answer mappings", "quizExercise", "invalidMappings");
            }
            ShortAnswerMapping mapping = new ShortAnswerMapping();
            mapping.setSpot(spot);
            mapping.setSolution(solution);
            return mapping;
        }).toList());
        question.setCorrectMappings(mappings);
        return question;
    }
}
