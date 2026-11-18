package de.tum.cit.aet.artemis.programming.web.open;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingTriggerService;

/**
 * Unit tests for the unauthenticated endpoint an external CI system posts build results to.
 * <p>
 * The endpoint carries no user session: the shared token in the header is the only thing standing between the public
 * internet and the ability to write results onto any participation. That makes the token check, and the fact that
 * nothing happens before it, the most important behaviour here. The rest of the method decides what a result means -
 * a build of the solution repository after a change to the tests has to rebuild the template as well, because the
 * template is expected to fail the new tests and its stale result would otherwise claim it still passes.
 */
@ExtendWith(MockitoExtension.class)
class PublicProgrammingExerciseResultResourceTest {

    private static final String TOKEN = "a-sufficiently-long-token";

    private static final long EXERCISE_ID = 7L;

    @Mock
    private ContinuousIntegrationService continuousIntegrationService;

    @Mock
    private ProgrammingExerciseGradingService programmingExerciseGradingService;

    @Mock
    private ProgrammingTriggerService programmingTriggerService;

    @Mock
    private ProgrammingMessagingService programmingMessagingService;

    @Mock
    private ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private PublicProgrammingExerciseResultResource resultResource;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        resultResource = new PublicProgrammingExerciseResultResource(Optional.of(continuousIntegrationService), programmingExerciseGradingService, programmingTriggerService,
                programmingMessagingService, programmingExerciseParticipationService, TOKEN);
        exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
    }

    @AfterEach
    void clearSecurityContext() {
        // The endpoint installs a stand-in authentication because no user is logged in; it must not leak into the next test.
        SecurityContextHolder.clearContext();
    }

    private void withPlanKey(String planKey) {
        lenient().when(continuousIntegrationService.getPlanKey(any())).thenReturn(planKey);
    }

    private static ProgrammingSubmission submission(SubmissionType type, String commitHash) {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setId(50L);
        submission.setType(type);
        submission.setCommitHash(commitHash);
        return submission;
    }

    private static Result resultFor(ProgrammingSubmission submission) {
        Result result = new Result();
        result.setId(90L);
        result.setSubmission(submission);
        submission.addResult(result);
        return result;
    }

    @Test
    void aTokenThatIsNotTheConfiguredOneIsRefusedBeforeAnythingIsRead() {
        // Nothing about the request body may be touched before the caller has proven it is the CI system.
        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> resultResource.processNewProgrammingExerciseResult("the-wrong-token", "{}"));

        verify(continuousIntegrationService, never()).getPlanKey(any());
        verify(programmingExerciseGradingService, never()).processNewProgrammingExerciseResult(any(), any());
    }

    @Test
    void aTokenThatIsOnlyAPrefixOfTheConfiguredOneIsRefused() {
        // The comparison hashes both sides, so a prefix must not pass the way a naive startsWith comparison would let it.
        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> resultResource.processNewProgrammingExerciseResult(TOKEN.substring(0, 12), "{}"));
    }

    @Test
    void anEmptyTokenIsRefused() {
        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> resultResource.processNewProgrammingExerciseResult("", "{}"));
    }

    @Test
    void aServerConfiguredWithATokenThatIsTooShortRefusesToStart() {
        // A short token is guessable, and this endpoint is reachable without a session, so the server must not come up with one.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new PublicProgrammingExerciseResultResource(Optional.of(continuousIntegrationService),
                programmingExerciseGradingService, programmingTriggerService, programmingMessagingService, programmingExerciseParticipationService, "too-short"));
    }

    @Test
    void aServerConfiguredWithoutATokenRefusesToStart() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new PublicProgrammingExerciseResultResource(Optional.of(continuousIntegrationService),
                programmingExerciseGradingService, programmingTriggerService, programmingMessagingService, programmingExerciseParticipationService, null));
    }

    @Test
    void aRequestBodyTheCiSystemCannotBeParsedFromIsReportedAsABadRequest() {
        when(continuousIntegrationService.getPlanKey(any())).thenThrow(new ContinuousIntegrationException("no plan key in there"));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> resultResource.processNewProgrammingExerciseResult(TOKEN, "{}"));
    }

    @Test
    void aResultForABuildPlanNoParticipationOwnsIsReported() {
        // Accepting it would write a result that belongs to nobody, so the CI system is told the plan is unknown instead.
        withPlanKey("ABC-GE12ABC");
        when(programmingExerciseParticipationService.getParticipationWithResults("ABC-GE12ABC")).thenReturn(null);

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> resultResource.processNewProgrammingExerciseResult(TOKEN, "{}"));
    }

    @Test
    void aResultForAStudentParticipationIsGradedAndTheStudentIsNotified() {
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setProgrammingExercise(exercise);
        var result = resultFor(submission(SubmissionType.MANUAL, "abc123"));
        withPlanKey("ABC-GE12ABC");
        when(programmingExerciseParticipationService.getParticipationWithResults("ABC-GE12ABC")).thenReturn(participation);
        when(programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, "{}")).thenReturn(result);

        var response = resultResource.processNewProgrammingExerciseResult(TOKEN, "{}");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(programmingMessagingService).notifyUserAboutNewResult(result, participation);
        verify(programmingTriggerService, never()).triggerTemplateBuildAndNotifyUser(anyLong(), anyString(), any());
    }

    @Test
    void aBuildThatProducedNoResultNotifiesNobody() {
        // Grading returns null when the build could not be turned into a result; notifying then would push an empty result to the client.
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setProgrammingExercise(exercise);
        withPlanKey("ABC-GE12ABC");
        when(programmingExerciseParticipationService.getParticipationWithResults("ABC-GE12ABC")).thenReturn(participation);
        when(programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, "{}")).thenReturn(null);

        var response = resultResource.processNewProgrammingExerciseResult(TOKEN, "{}");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(programmingMessagingService, never()).notifyUserAboutNewResult(any(), any());
    }

    @Test
    void aSolutionBuildCausedByAChangeToTheTestsAlsoRebuildsTheTemplate() {
        // The template is expected to fail the new tests; without this its previous result would keep claiming it passes them.
        var participation = new SolutionProgrammingExerciseParticipation();
        participation.setProgrammingExercise(exercise);
        var testSubmission = submission(SubmissionType.TEST, "deadbeef");
        var result = resultFor(testSubmission);
        withPlanKey("ABC-SOLUTION");
        when(programmingExerciseParticipationService.getParticipationWithResults("ABC-SOLUTION")).thenReturn(participation);
        when(programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, "{}")).thenReturn(result);

        resultResource.processNewProgrammingExerciseResult(TOKEN, "{}");

        verify(programmingTriggerService).triggerTemplateBuildAndNotifyUser(EXERCISE_ID, "deadbeef", SubmissionType.TEST);
        verify(programmingMessagingService).notifyUserAboutNewResult(result, participation);
    }

    @Test
    void aSolutionBuildCausedByAChangeToTheSolutionItselfDoesNotRebuildTheTemplate() {
        // The template is unaffected by a change to the solution, and rebuilding every time would double the builds of an exercise.
        var participation = new SolutionProgrammingExerciseParticipation();
        participation.setProgrammingExercise(exercise);
        var result = resultFor(submission(SubmissionType.MANUAL, "abc123"));
        withPlanKey("ABC-SOLUTION");
        when(programmingExerciseParticipationService.getParticipationWithResults("ABC-SOLUTION")).thenReturn(participation);
        when(programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, "{}")).thenReturn(result);

        resultResource.processNewProgrammingExerciseResult(TOKEN, "{}");

        verify(programmingTriggerService, never()).triggerTemplateBuildAndNotifyUser(anyLong(), anyString(), any());
    }

    @Test
    void anExerciseWithoutATemplateParticipationStillAcceptsTheSolutionResult() {
        // The result that just arrived is worth keeping even if the follow-up build cannot be triggered.
        var participation = new SolutionProgrammingExerciseParticipation();
        participation.setProgrammingExercise(exercise);
        var result = resultFor(submission(SubmissionType.TEST, "deadbeef"));
        withPlanKey("ABC-SOLUTION");
        when(programmingExerciseParticipationService.getParticipationWithResults("ABC-SOLUTION")).thenReturn(participation);
        when(programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, "{}")).thenReturn(result);
        doThrow(new EntityNotFoundException("no template participation")).when(programmingTriggerService).triggerTemplateBuildAndNotifyUser(anyLong(), anyString(), any());

        assertThatCode(() -> resultResource.processNewProgrammingExerciseResult(TOKEN, "{}")).doesNotThrowAnyException();

        verify(programmingMessagingService).notifyUserAboutNewResult(result, participation);
    }
}
