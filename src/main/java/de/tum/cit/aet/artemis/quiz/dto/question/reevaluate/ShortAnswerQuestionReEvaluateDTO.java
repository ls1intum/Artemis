package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerQuestionReEvaluateDTO(@NotNull Long id, @NotBlank String title, @NotNull String text, @NotNull ScoringType scoringType, @NotNull Boolean randomizeOrder,
        @NotNull Boolean invalid, @NotNull Integer similarityValue, @NotNull Boolean matchLetterCase, @NotEmpty List<@Valid ShortAnswerSpotReEvaluateDTO> spots,
        @NotEmpty List<@Valid ShortAnswerSolutionReEvaluateDTO> solutions, @NotEmpty List<@Valid ShortAnswerMappingReEvaluateDTO> correctMappings)
        implements QuizQuestionReEvaluateDTO {

    /**
     * Creates a ShortAnswerQuestionReEvaluateDTO from the given ShortAnswerQuestion.
     *
     * @param shortAnswerQuestion the short answer question to convert
     * @return the corresponding DTO
     */
    public static ShortAnswerQuestionReEvaluateDTO of(ShortAnswerQuestion shortAnswerQuestion) {
        // Generate tempIDs for new solutions (id=null) at the DTO layer
        long tempIdCounter = 1;
        Map<ShortAnswerSolution, Long> solutionTempIds = new HashMap<>();
        for (ShortAnswerSolution sol : shortAnswerQuestion.getSolutions()) {
            if (sol.getId() == null) {
                solutionTempIds.put(sol, tempIdCounter++);
            }
        }

        List<ShortAnswerSolutionReEvaluateDTO> solutionDTOs = shortAnswerQuestion.getSolutions().stream()
                .map(sol -> new ShortAnswerSolutionReEvaluateDTO(sol.getId(), solutionTempIds.get(sol), sol.getText(), sol.isInvalid())).toList();

        List<ShortAnswerMappingReEvaluateDTO> mappingDTOs = shortAnswerQuestion.getCorrectMappings().stream().map(m -> {
            Long solutionId = m.getSolution().getId();
            Long solutionTempID = solutionTempIds.get(m.getSolution());
            return new ShortAnswerMappingReEvaluateDTO(solutionId, solutionTempID, m.getSpot().getId());
        }).toList();

        return new ShortAnswerQuestionReEvaluateDTO(shortAnswerQuestion.getId(), shortAnswerQuestion.getTitle(), shortAnswerQuestion.getText(),
                shortAnswerQuestion.getScoringType(), shortAnswerQuestion.isRandomizeOrder(), shortAnswerQuestion.isInvalid(), shortAnswerQuestion.getSimilarityValue(),
                shortAnswerQuestion.getMatchLetterCase(), shortAnswerQuestion.getSpots().stream().map(ShortAnswerSpotReEvaluateDTO::of).toList(), solutionDTOs, mappingDTOs);
    }
}
