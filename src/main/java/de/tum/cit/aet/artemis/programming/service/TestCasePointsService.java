package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.util.RoundingUtil;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;

/**
 * Derives the points a test case awards from the current grading configuration (weight, bonus multiplier,
 * bonus points, exercise max points). Test-case feedback does not store credits — every reader (score
 * calculation, DTO assembly) obtains them through this service, which makes each read behave as if the
 * exercise had just been re-evaluated.
 * <p>
 * Deliberately dependency-light (only the test-case repository) so that serialization-side consumers such
 * as the feedback synthesizer do not pull the full grading service into their bean graph.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class TestCasePointsService {

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    public TestCasePointsService(ProgrammingExerciseTestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
    }

    /**
     * Calculates the points that should be awarded for a successful test case.
     *
     * @param testCase                for which the points should be calculated
     * @param testCases               all test cases relevant for the score (visibility != NEVER included;
     *                                    the weight sum is computed over the non-invisible ones)
     * @param exercise                the programming exercise
     * @param weightSum               the precomputed weight sum over the non-invisible test cases
     * @param isSolutionParticipation true if the result belongs to the solution participation (with a
     *                                    weight sum of zero, its test cases are weighted equally so that a
     *                                    fully working solution still shows 100%)
     * @return the points which should be awarded for successfully completing the test case
     */
    public double calculatePointsForTestCase(final ProgrammingExerciseTestCase testCase, Set<ProgrammingExerciseTestCase> testCases, ProgrammingExercise exercise, double weightSum,
            boolean isSolutionParticipation) {
        final int totalTestCaseCount = testCases.size();

        final boolean isWeightSumZero = RoundingUtil.equalsWithinEpsilon(weightSum, 0, 1E-8);
        final double testPoints;
        double exerciseMaxPoints = exercise.getMaxPoints();

        // In case of a weight-sum of zero the instructor must be able to distinguish between a working solution
        // (all tests passed, 0 points) and a solution with test failures.
        // Only the second case should show a warning while the first case is considered as 100%.
        // Therefore, all test cases have equal weight in such a case.
        if (isWeightSumZero && isSolutionParticipation) {
            testPoints = (1.0 / totalTestCaseCount) * exerciseMaxPoints;
        }
        else if (isWeightSumZero) {
            // this test case must have zero weight as well; avoid division by zero
            testPoints = 0D;
        }
        else {
            double testWeight = testCase.getWeight() * testCase.getBonusMultiplier();
            testPoints = (testWeight / weightSum) * exerciseMaxPoints;
        }

        return testPoints + testCase.getBonusPoints();
    }

    /**
     * Calculates the weight sum over the non-invisible test cases; the input to the points formula.
     *
     * @param testCases the test cases of the exercise
     * @return the weight sum
     */
    public static double calculateWeightSum(final Set<ProgrammingExerciseTestCase> testCases) {
        return testCases.stream().filter(testCase -> !testCase.isInvisible()).mapToDouble(ProgrammingExerciseTestCase::getWeight).sum();
    }

    /**
     * Derives the points per test-case id for the given test cases.
     *
     * @param testCases               the test cases relevant for the score
     * @param exercise                the programming exercise
     * @param isSolutionParticipation true if the result belongs to the solution participation
     * @return derived points per test-case id
     */
    public Map<Long, Double> calculateTestCasePoints(Set<ProgrammingExerciseTestCase> testCases, ProgrammingExercise exercise, boolean isSolutionParticipation) {
        double weightSum = calculateWeightSum(testCases);
        return testCases.stream().filter(testCase -> testCase.getId() != null).collect(Collectors.toMap(ProgrammingExerciseTestCase::getId,
                testCase -> calculatePointsForTestCase(testCase, testCases, exercise, weightSum, isSolutionParticipation), (first, second) -> first));
    }

    /**
     * Derives the points per test-case id for a result of the given exercise, loading the exercise's active
     * test cases. Convenience variant for callers outside the grading flow (manual assessment, DTO assembly).
     * <p>
     * Precondition for the zero-weight-sum special case: the result's submission and participation must be
     * reachable (loaded or within an open session) — see {@link #isForSolutionParticipation(Result)}.
     * Callers that process many results of one exercise should use
     * {@link #calculateTestCasePoints(ProgrammingExercise, boolean)} instead and reuse the map, because
     * this variant loads the exercise's test cases on every call.
     *
     * @param exercise the programming exercise
     * @param result   the result whose participation determines the zero-weight-sum special case
     * @return derived points per test-case id
     */
    public Map<Long, Double> calculateTestCasePoints(ProgrammingExercise exercise, Result result) {
        return calculateTestCasePoints(exercise, isForSolutionParticipation(result));
    }

    /**
     * Derives the points per test-case id for the given exercise, loading its active test cases.
     *
     * @param exercise                the programming exercise
     * @param isSolutionParticipation true if the map is used for results of the solution participation
     * @return derived points per test-case id
     */
    public Map<Long, Double> calculateTestCasePoints(ProgrammingExercise exercise, boolean isSolutionParticipation) {
        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseIdAndActive(exercise.getId(), true);
        return calculateTestCasePoints(testCases, exercise, isSolutionParticipation);
    }

    /**
     * Whether the given result belongs to the solution participation (which gets the zero-weight-sum
     * special treatment). Returns {@code false} when the submission or participation is not reachable.
     *
     * @param result the result to check (may be {@code null})
     * @return true if the result belongs to a solution participation
     */
    public static boolean isForSolutionParticipation(Result result) {
        return result != null && result.getSubmission() != null && result.getSubmission().getParticipation() instanceof SolutionProgrammingExerciseParticipation;
    }
}
