package de.tum.cit.aet.artemis.exercise.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Unit tests for the result accessors of {@link Submission}.
 * <p>
 * {@link Submission#getLatestResult()} resolves by highest id and can therefore return an automatic or Athena result,
 * which is never a correction round and never carries an assessor. Operations that act on what a tutor is assessing need
 * {@link Submission#getLatestManualResult()} instead.
 */
class SubmissionResultAccessorTest {

    private static Result result(long id, AssessmentType assessmentType) {
        Result result = new Result();
        result.setId(id);
        result.setAssessmentType(assessmentType);
        return result;
    }

    private static Submission submissionWith(Result... results) {
        Submission submission = new TextSubmission();
        submission.setResults(new HashSet<>(Arrays.asList(results)));
        return submission;
    }

    @Test
    void latestManualResultSkipsANewerAthenaResult() {
        Result manual = result(1L, AssessmentType.MANUAL);
        Result athena = result(2L, AssessmentType.AUTOMATIC_ATHENA);
        Submission submission = submissionWith(manual, athena);

        assertThat(submission.getLatestResult()).as("resolving by id returns the Athena result").isEqualTo(athena);
        assertThat(submission.getLatestManualResult()).as("the newest correction round is the manual result").isEqualTo(manual);
    }

    @Test
    void latestManualResultReturnsTheNewestCorrectionRound() {
        Result firstRound = result(1L, AssessmentType.MANUAL);
        Result secondRound = result(2L, AssessmentType.SEMI_AUTOMATIC);
        Submission submission = submissionWith(firstRound, secondRound);

        assertThat(submission.getLatestManualResult()).isEqualTo(secondRound);
        assertThat(submission.getFirstManualResult()).isEqualTo(firstRound);
    }

    @Test
    void manualResultAccessorsReturnNullWhenOnlyAutomaticResultsExist() {
        Submission submission = submissionWith(result(1L, AssessmentType.AUTOMATIC), result(2L, AssessmentType.AUTOMATIC_ATHENA));

        // Both used to be reachable with a non-empty result list; getFirstManualResult() then threw instead of returning null.
        assertThat(submission.getLatestManualResult()).isNull();
        assertThat(submission.getFirstManualResult()).isNull();
    }

    @Test
    void manualResultAccessorsReturnNullWithoutResults() {
        Submission submission = submissionWith();

        assertThat(submission.getLatestManualResult()).isNull();
        assertThat(submission.getFirstManualResult()).isNull();
    }

    @Test
    void manualResultAccessorsToleratePlaceholdersLeftByDeletedResults() {
        Result manual = result(2L, AssessmentType.MANUAL);
        Submission submission = new TextSubmission();
        // The results are defensively null tolerant, so a null among them must not break the accessors.
        submission.setResults(new HashSet<>());
        submission.getResults().add(null);
        submission.getResults().add(manual);

        assertThat(submission.getLatestManualResult()).isEqualTo(manual);
        assertThat(submission.getFirstManualResult()).isEqualTo(manual);
    }
}
