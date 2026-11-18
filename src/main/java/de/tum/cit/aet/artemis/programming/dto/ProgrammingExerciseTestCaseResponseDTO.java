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
 *
 * @param id              the test case id
 * @param testName        the name of the test case as reported by the build
 * @param weight          the weight of the test case in the score calculation
 * @param bonusMultiplier the bonus multiplier, defaulted to {@code 1.0} by the entity getter
 * @param bonusPoints     the bonus points, defaulted to {@code 0.0} by the entity getter
 * @param active          whether the test case counts towards the score
 * @param visibility      when the test case is shown to students
 * @param type            the kind of test case (structural, behavioral, ...)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseTestCaseResponseDTO(Long id, String testName, Double weight, Double bonusMultiplier, Double bonusPoints, Boolean active, Visibility visibility,
        ProgrammingExerciseTestCaseType type) {

    /**
     * Converts a test case into its response shape.
     *
     * @param testCase the test case to convert
     * @return the converted DTO
     */
    public static ProgrammingExerciseTestCaseResponseDTO of(ProgrammingExerciseTestCase testCase) {
        return new ProgrammingExerciseTestCaseResponseDTO(testCase.getId(), testCase.getTestName(), testCase.getWeight(), testCase.getBonusMultiplier(), testCase.getBonusPoints(),
                testCase.isActive(), testCase.getVisibility(), testCase.getType());
    }
}
