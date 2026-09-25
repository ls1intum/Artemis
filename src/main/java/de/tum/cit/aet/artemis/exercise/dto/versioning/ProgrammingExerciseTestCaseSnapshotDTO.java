package de.tum.cit.aet.artemis.exercise.dto.versioning;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCaseType;

/** Versioned test identity and grading, independent of the mutable test-case table. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProgrammingExerciseTestCaseSnapshotDTO(Long id, String testName, Boolean active, ProgrammingExerciseTestCaseType type, Double weight, Double bonusMultiplier,
        Double bonusPoints, Visibility visibility) implements Serializable {

    /**
     * Captures test identity as well as grading; older stored snapshots deserialize the added fields as null.
     *
     * @param testCase the test to snapshot
     * @return detached test state
     */
    public static ProgrammingExerciseTestCaseSnapshotDTO of(ProgrammingExerciseTestCase testCase) {
        return new ProgrammingExerciseTestCaseSnapshotDTO(testCase.getId(), testCase.getTestName(), testCase.isActive(), testCase.getType(), testCase.getWeight(),
                testCase.getBonusMultiplier(), testCase.getBonusPoints(), testCase.getVisibility());
    }
}
