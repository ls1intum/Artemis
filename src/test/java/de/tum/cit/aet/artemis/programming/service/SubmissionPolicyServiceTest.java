package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;

/**
 * Unit tests for the submission policies that limit how often a student may submit.
 * <p>
 * A lock repository policy stops accepting submissions once the limit is reached, and a submission penalty policy keeps
 * accepting them but deducts points for every submission beyond the limit. Both decide what a student is graded on, so
 * the arithmetic behind them - how submissions are counted, what is deducted, and what the student is told - is what
 * these tests pin down.
 */
@ExtendWith(MockitoExtension.class)
class SubmissionPolicyServiceTest {

    private static final long PARTICIPATION_ID = 7L;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private SubmissionPolicyRepository submissionPolicyRepository;

    @Mock
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Mock
    private ParticipationTestRepository participationRepository;

    @InjectMocks
    private SubmissionPolicyService submissionPolicyService;

    private static LockRepositoryPolicy lockRepositoryPolicy(int submissionLimit, boolean active) {
        LockRepositoryPolicy policy = new LockRepositoryPolicy();
        policy.setSubmissionLimit(submissionLimit);
        policy.setActive(active);
        return policy;
    }

    private static SubmissionPenaltyPolicy penaltyPolicy(int submissionLimit, double exceedingPenalty, boolean active) {
        SubmissionPenaltyPolicy policy = new SubmissionPenaltyPolicy();
        policy.setSubmissionLimit(submissionLimit);
        policy.setExceedingPenalty(exceedingPenalty);
        policy.setActive(active);
        return policy;
    }

    private static Participation participation() {
        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(PARTICIPATION_ID);
        return participation;
    }

    /** Makes the participation look as if the student had already produced the given number of counted submissions. */
    private void withCountedSubmissions(int count) {
        when(programmingSubmissionRepository.findDistinctManualCommitHashesWithResultByParticipationId(PARTICIPATION_ID))
                .thenReturn(java.util.stream.IntStream.range(0, count).mapToObj(i -> "commit" + i).toList());
        // The newest result is not stored yet when a policy is evaluated, so the count is read through the compensating overload.
        Participation loadedParticipation = participation();
        loadedParticipation.setSubmissions(Set.of());
        when(participationRepository.findByIdWithLatestSubmissionAndResult(PARTICIPATION_ID)).thenReturn(Optional.of(loadedParticipation));
    }

    @Test
    void validateSubmissionPolicy_withoutAnActivationFlag_isRejected() {
        SubmissionPolicy policyWithoutActivation = lockRepositoryPolicy(5, true);
        policyWithoutActivation.setActive(null);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> submissionPolicyService.validateSubmissionPolicy(policyWithoutActivation))
                .withMessageContaining("Activation cannot be null");
    }

    @Test
    void validateSubmissionPolicy_withoutAPositiveSubmissionLimit_isRejected() {
        // A limit of zero or less would lock every student out before their first submission.
        for (Integer illegalLimit : new Integer[] { null, 0, -1 }) {
            LockRepositoryPolicy policy = lockRepositoryPolicy(1, true);
            policy.setSubmissionLimit(illegalLimit);

            assertThatExceptionOfType(BadRequestAlertException.class).as("a submission limit of %s must be rejected", illegalLimit)
                    .isThrownBy(() -> submissionPolicyService.validateSubmissionPolicy(policy)).withMessageContaining("must be greater than 0");
        }
    }

    @Test
    void validateSubmissionPolicy_forAPenaltyPolicyWithoutAPenalty_isRejected() {
        // A penalty policy that deducts nothing is indistinguishable from having no policy at all, which is not what the instructor configured.
        for (Double illegalPenalty : new Double[] { null, 0.0, -1.0 }) {
            SubmissionPenaltyPolicy policy = penaltyPolicy(5, 1.0, true);
            policy.setExceedingPenalty(illegalPenalty);

            assertThatExceptionOfType(BadRequestAlertException.class).as("a penalty of %s must be rejected", illegalPenalty)
                    .isThrownBy(() -> submissionPolicyService.validateSubmissionPolicy(policy)).withMessageContaining("penalty of submission penalty policies");
        }
    }

    @Test
    void validateSubmissionPolicy_forAValidPolicy_isAccepted() {
        submissionPolicyService.validateSubmissionPolicy(lockRepositoryPolicy(5, true));
        submissionPolicyService.validateSubmissionPolicy(penaltyPolicy(5, 2.5, false));
    }

    @Test
    void validateSubmissionPolicyCreation_activatesThePolicyAndWiresItToTheExercise() {
        // The client sends policies inactive to keep them from taking effect while an exercise is still being created; creation is where they are turned on.
        ProgrammingExercise exercise = new ProgrammingExercise();
        SubmissionPenaltyPolicy policy = penaltyPolicy(5, 2.0, false);
        exercise.setSubmissionPolicy(policy);
        when(submissionPolicyRepository.save(policy)).thenReturn(policy);

        submissionPolicyService.validateSubmissionPolicyCreation(exercise);

        assertThat(exercise.getSubmissionPolicy().isActive()).as("a policy given at creation is active from the start").isTrue();
        assertThat(exercise.getSubmissionPolicy().getProgrammingExercise()).as("the policy knows the exercise it belongs to").isEqualTo(exercise);
    }

    @Test
    void validateSubmissionPolicyCreation_forAnExerciseWithoutAPolicy_doesNothing() {
        submissionPolicyService.validateSubmissionPolicyCreation(new ProgrammingExercise());

        verifyNoInteractions(submissionPolicyRepository);
    }

    @Test
    void enableAndDisableSubmissionPolicy_toggleThePolicyOfEitherKind() {
        LockRepositoryPolicy lockPolicy = lockRepositoryPolicy(5, false);
        SubmissionPenaltyPolicy penaltyPolicy = penaltyPolicy(5, 1.0, false);
        when(submissionPolicyRepository.save(any(SubmissionPolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        submissionPolicyService.enableSubmissionPolicy(lockPolicy);
        submissionPolicyService.enableSubmissionPolicy(penaltyPolicy);
        assertThat(lockPolicy.isActive()).as("a lock repository policy is enabled").isTrue();
        assertThat(penaltyPolicy.isActive()).as("a submission penalty policy is enabled").isTrue();

        submissionPolicyService.disableSubmissionPolicy(lockPolicy);
        submissionPolicyService.disableSubmissionPolicy(penaltyPolicy);
        assertThat(lockPolicy.isActive()).as("a lock repository policy is disabled again").isFalse();
        assertThat(penaltyPolicy.isActive()).as("a submission penalty policy is disabled again").isFalse();
    }

    @Test
    void enableSubmissionPolicy_forAPolicyKindThatDoesNotExist_isReportedRatherThanSilentlyIgnored() {
        // A policy kind the service does not know about must not be toggled silently; the caller has to learn that nothing happened.
        SubmissionPolicy unknownKind = new SubmissionPolicy() {

            @Override
            public String toString() {
                return "a submission policy of a kind the service does not know";
            }
        };

        assertThatExceptionOfType(NotImplementedException.class).isThrownBy(() -> submissionPolicyService.enableSubmissionPolicy(unknownKind));
        assertThatExceptionOfType(NotImplementedException.class).isThrownBy(() -> submissionPolicyService.disableSubmissionPolicy(unknownKind));
    }

    @Test
    void removeSubmissionPolicyFromProgrammingExercise_disablesThePolicyBeforeDroppingIt() {
        // Dropping the policy without disabling it first would leave its effect on the participations in place.
        ProgrammingExercise exercise = new ProgrammingExercise();
        LockRepositoryPolicy policy = lockRepositoryPolicy(5, true);
        exercise.setSubmissionPolicy(policy);
        when(submissionPolicyRepository.save(any(SubmissionPolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        submissionPolicyService.removeSubmissionPolicyFromProgrammingExercise(exercise);

        assertThat(policy.isActive()).as("the policy is disabled on the way out").isFalse();
        assertThat(exercise.getSubmissionPolicy()).as("the exercise no longer has a policy").isNull();
        verify(programmingExerciseRepository).save(exercise);
    }

    @Test
    void calculateSubmissionPenalty_deductsOnlyForTheSubmissionsBeyondTheLimit() {
        withCountedSubmissions(7);

        double penalty = submissionPolicyService.calculateSubmissionPenalty(participation(), penaltyPolicy(5, 2.0, true));

        assertThat(penalty).as("two submissions beyond the limit of five deduct twice the penalty").isEqualTo(4.0);
    }

    @Test
    void calculateSubmissionPenalty_withinTheLimitOrForAnInactivePolicy_deductsNothing() {
        withCountedSubmissions(3);
        assertThat(submissionPolicyService.calculateSubmissionPenalty(participation(), penaltyPolicy(5, 2.0, true))).as("a student within the limit is not penalised").isEqualTo(0);

        assertThat(submissionPolicyService.calculateSubmissionPenalty(participation(), penaltyPolicy(5, 2.0, false))).as("an inactive policy deducts nothing").isEqualTo(0);
        assertThat(submissionPolicyService.calculateSubmissionPenalty(participation(), null)).as("no policy deducts nothing").isEqualTo(0);
    }

    @Test
    void handleLockRepositoryPolicy_marksAResultBeyondTheLimitAsUnrated() {
        // The version control system locks the repository at the limit; this is the fallback for when it did not, so the extra submission must not count.
        withCountedSubmissions(6);
        Result result = new Result();
        result.setRated(true);

        submissionPolicyService.handleLockRepositoryPolicy(result, participation(), lockRepositoryPolicy(5, true));

        assertThat(result.isRated()).as("a submission past the limit is not rated").isFalse();
    }

    @Test
    void handleLockRepositoryPolicy_withinTheLimitOrForAnInactivePolicy_leavesTheResultRated() {
        withCountedSubmissions(5);
        Result result = new Result();
        result.setRated(true);

        submissionPolicyService.handleLockRepositoryPolicy(result, participation(), lockRepositoryPolicy(5, true));
        assertThat(result.isRated()).as("the submission that reaches the limit still counts").isTrue();

        submissionPolicyService.handleLockRepositoryPolicy(result, participation(), lockRepositoryPolicy(1, false));
        submissionPolicyService.handleLockRepositoryPolicy(result, participation(), null);
        assertThat(result.isRated()).as("an inactive policy and no policy leave the result alone").isTrue();
    }

    @Test
    void createFeedbackForPenaltyPolicy_tellsTheStudentHowMuchWasDeductedAndWhy() {
        withCountedSubmissions(8);
        Result result = resultWithParticipation();

        submissionPolicyService.createFeedbackForPenaltyPolicy(result, penaltyPolicy(5, 2.0, true));

        assertThat(result.getFeedbacks()).as("the deduction is reported as feedback").hasSize(1);
        Feedback feedback = result.getFeedbacks().iterator().next();
        assertThat(feedback.getCredits()).as("three submissions beyond the limit deduct six points").isEqualTo(-6.0);
        assertThat(feedback.isPositive()).isFalse();
        assertThat(feedback.getType()).isEqualTo(FeedbackType.AUTOMATIC);
        assertThat(feedback.getText()).as("the feedback is marked as coming from a submission policy").startsWith(Feedback.SUBMISSION_POLICY_FEEDBACK_IDENTIFIER);
        assertThat(feedback.getDetailText()).as("the student is told how far past the limit they are and what it cost")
                .isEqualTo("You have submitted 3 more times than the submission limit of 5. This results in a deduction of 6.0 points!");
    }

    @Test
    void createFeedbackForPenaltyPolicy_forASingleExcessSubmission_usesTheSingular() {
        withCountedSubmissions(6);
        Result result = resultWithParticipation();

        submissionPolicyService.createFeedbackForPenaltyPolicy(result, penaltyPolicy(5, 1.5, true));

        assertThat(result.getFeedbacks().iterator().next().getDetailText()).as("one submission too many reads as 'one more time', not 'one more times'")
                .isEqualTo("You have submitted 1 more time than the submission limit of 5. This results in a deduction of 1.5 points!");
    }

    @Test
    void createFeedbackForPenaltyPolicy_withinTheLimitOrForAnInactivePolicy_addsNoFeedback() {
        withCountedSubmissions(4);
        Result result = resultWithParticipation();

        submissionPolicyService.createFeedbackForPenaltyPolicy(result, penaltyPolicy(5, 2.0, true));
        assertThat(result.getFeedbacks()).as("a student within the limit is told nothing").isEmpty();

        submissionPolicyService.createFeedbackForPenaltyPolicy(result, penaltyPolicy(1, 2.0, false));
        submissionPolicyService.createFeedbackForPenaltyPolicy(result, null);
        assertThat(result.getFeedbacks()).as("an inactive policy and no policy add nothing").isEmpty();
    }

    @Test
    void getParticipationSubmissionCount_countsTheSubmissionThatIsCurrentlyBeingGraded() {
        // The result for the newest submission is not written yet while a policy is evaluated, so that submission has to be counted on top of the stored ones.
        when(programmingSubmissionRepository.findDistinctManualCommitHashesWithResultByParticipationId(PARTICIPATION_ID)).thenReturn(List.of("commit1", "commit2"));
        Participation loadedParticipation = participation();
        ProgrammingSubmission submissionBeingGraded = new ProgrammingSubmission();
        submissionBeingGraded.setResults(Set.of());
        loadedParticipation.setSubmissions(Set.of(submissionBeingGraded));
        when(participationRepository.findByIdWithLatestSubmissionAndResult(PARTICIPATION_ID)).thenReturn(Optional.of(loadedParticipation));

        assertThat(submissionPolicyService.getParticipationSubmissionCount(participation())).as("the submission being graded counts towards the limit").isEqualTo(3);
    }

    @Test
    void getParticipationSubmissionCount_withoutCompensation_countsOnlyTheStoredSubmissions() {
        when(programmingSubmissionRepository.findDistinctManualCommitHashesWithResultByParticipationId(PARTICIPATION_ID)).thenReturn(List.of("commit1", "commit2"));

        assertThat(submissionPolicyService.getParticipationSubmissionCount(participation(), false)).as("without compensation only the stored submissions are counted").isEqualTo(2);
        verify(participationRepository, never()).findByIdWithLatestSubmissionAndResult(PARTICIPATION_ID);
    }

    private Result resultWithParticipation() {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setParticipation(participation());
        Result result = new Result();
        result.setSubmission(submission);
        submission.setResults(new java.util.HashSet<>(Set.of(result)));
        return result;
    }
}
