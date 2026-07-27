package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCaseType;

/**
 * DTO for reading a programming exercise test case (grading table, test-case websocket push, and nested inside
 * {@link ProgrammingExerciseTaskDTO}).
 * <p>
 * Maps {@code weight}/{@code bonusMultiplier}/{@code bonusPoints} through the entity getters, not the raw fields:
 * {@link ProgrammingExerciseTestCase#getBonusMultiplier()} and {@link ProgrammingExerciseTestCase#getBonusPoints()}
 * substitute the {@code 1.0}/{@code 0.0} defaults the grading table renders for a {@code null} column.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseTestCaseResponseDTO(Long id, String testName, Double weight, Double bonusMultiplier, Double bonusPoints, Boolean active, Visibility visibility,
        ProgrammingExerciseTestCaseType type) {

    public static ProgrammingExerciseTestCaseResponseDTO of(ProgrammingExerciseTestCase testCase) {
        return new ProgrammingExerciseTestCaseResponseDTO(testCase.getId(), testCase.getTestName(), testCase.getWeight(), testCase.getBonusMultiplier(), testCase.getBonusPoints(),
                testCase.isActive(), testCase.getVisibility(), testCase.getType());
    }
}
