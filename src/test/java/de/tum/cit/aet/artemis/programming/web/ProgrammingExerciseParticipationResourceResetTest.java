package de.tum.cit.aet.artemis.programming.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exam.api.ExamApi;
import de.tum.cit.aet.artemis.exam.api.StudentExamApi;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.localci.service.SharedQueueManagementService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.dto.CommitInfoDTO;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.VcsAccessLogRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Unit tests for resetting a student's repository and for reading its commit history.
 * <p>
 * Resetting throws the student's work away and replaces it with the template, so every guard around it matters: a
 * participation that is already locked must not be reset after the deadline, an exam repository must not be reset at
 * all, and resetting from another participation is only allowed when the student may read that participation too -
 * otherwise the reset would be a way to copy somebody else's solution into one's own repository.
 */
@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseParticipationResourceResetTest {

    private static final long PARTICIPATION_ID = 10L;

    private static final long EXERCISE_ID = 7L;

    @Mock
    private ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    @Mock
    private ResultTestRepository resultRepository;

    @Mock
    private ParticipationTestRepository participationRepository;

    @Mock
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    @Mock
    private ProgrammingSubmissionService submissionService;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private AuthorizationCheckService authCheckService;

    @Mock
    private ResultService resultService;

    @Mock
    private ParticipationAuthorizationCheckService participationAuthCheckService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private StudentExamApi studentExamApi;

    @Mock
    private VcsAccessLogRepository vcsAccessLogRepository;

    @Mock
    private AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Mock
    private SharedQueueManagementService sharedQueueManagementService;

    @Mock
    private ExamApi examApi;

    @Mock
    private ContinuousIntegrationTriggerService continuousIntegrationTriggerService;

    private ProgrammingExerciseParticipationResource resource;

    private ProgrammingExercise exercise;

    private ProgrammingExerciseStudentParticipation participation;

    @BeforeEach
    void setUp() throws Exception {
        resource = new ProgrammingExerciseParticipationResource(programmingExerciseParticipationService, resultRepository, participationRepository,
                programmingExerciseStudentParticipationRepository, submissionService, programmingExerciseRepository, authCheckService, resultService, participationAuthCheckService,
                repositoryService, Optional.of(studentExamApi), Optional.of(vcsAccessLogRepository), auxiliaryRepositoryRepository, Optional.of(sharedQueueManagementService),
                Optional.of(examApi), Optional.of(continuousIntegrationTriggerService));
        exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setTemplateParticipation(new TemplateProgrammingExerciseParticipation());
        exercise.setTemplateRepositoryUri("https://artemis.example.com/git/ABC/abc-exercise.git");
        participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(PARTICIPATION_ID);
        participation.setRepositoryUri("https://artemis.example.com/git/ABC/abc-student.git");
    }

    /**
     * A participation the student may work in: it exists, its exercise is a course exercise, and it is not locked.
     */
    private void withAResettableParticipation() {
        lenient().when(programmingExerciseStudentParticipationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);
        lenient().when(programmingExerciseRepository.findByStudentParticipationIdWithTemplateParticipation(PARTICIPATION_ID)).thenReturn(Optional.of(exercise));
        lenient().when(participationAuthCheckService.isLocked(participation, exercise)).thenReturn(false);
    }

    @Test
    void resettingAParticipationReplacesItsContentWithTheTemplateAndRebuildsIt() throws Exception {
        // Without the rebuild the student would keep the result of the work that was just thrown away.
        withAResettableParticipation();

        var response = resource.resetRepository(PARTICIPATION_ID, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(programmingExerciseParticipationService).resetRepository(participation.getVcsRepositoryUri(), exercise.getVcsTemplateRepositoryUri());
        verify(continuousIntegrationTriggerService).triggerBuild(participation, true);
    }

    @Test
    void resettingAParticipationWhoseExerciseIsGoneIsReported() throws Exception {
        when(programmingExerciseStudentParticipationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);
        when(programmingExerciseRepository.findByStudentParticipationIdWithTemplateParticipation(PARTICIPATION_ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> resource.resetRepository(PARTICIPATION_ID, null));
    }

    @Test
    void resettingAParticipationOfSomebodyElseIsRefused() throws Exception {
        withAResettableParticipation();
        doThrow(new AccessForbiddenException("not yours")).when(participationAuthCheckService).checkCanAccessParticipationElseThrow(participation);

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> resource.resetRepository(PARTICIPATION_ID, null));

        verify(programmingExerciseParticipationService, never()).resetRepository(any(), any());
    }

    @Test
    void resettingALockedParticipationIsRefused() throws Exception {
        // The repository is locked once the deadline has passed, and a reset would let the student start over afterwards.
        withAResettableParticipation();
        when(participationAuthCheckService.isLocked(participation, exercise)).thenReturn(true);

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> resource.resetRepository(PARTICIPATION_ID, null));

        verify(programmingExerciseParticipationService, never()).resetRepository(any(), any());
    }

    @Test
    void resettingAnExamRepositoryIsRefused() throws Exception {
        // Exam repositories are the record of what the student submitted under exam conditions and must not be overwritten.
        var exerciseGroup = new ExerciseGroup();
        exerciseGroup.setExam(new Exam());
        exercise.setExerciseGroup(exerciseGroup);
        exercise.setCourse(null);
        withAResettableParticipation();

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> resource.resetRepository(PARTICIPATION_ID, null));

        verify(programmingExerciseParticipationService, never()).resetRepository(any(), any());
    }

    @Test
    void resettingFromAGradedParticipationTakesItsRepositoryAsTheSource() throws Exception {
        // A practice run starts from what the student handed in for the grade, not from the empty template.
        withAResettableParticipation();
        var gradedParticipation = new ProgrammingExerciseStudentParticipation();
        gradedParticipation.setId(20L);
        gradedParticipation.setRepositoryUri("https://artemis.example.com/git/ABC/abc-graded.git");
        when(programmingExerciseStudentParticipationRepository.findByIdElseThrow(20L)).thenReturn(gradedParticipation);

        org.assertj.core.api.Assertions.assertThatCode(() -> resource.resetRepository(PARTICIPATION_ID, 20L)).doesNotThrowAnyException();

        verify(programmingExerciseParticipationService).resetRepository(participation.getVcsRepositoryUri(), gradedParticipation.getVcsRepositoryUri());
    }

    @Test
    void resettingFromAParticipationTheStudentMayNotReadIsRefused() throws Exception {
        // Without this check a reset would be a way to copy another student's solution into one's own repository.
        withAResettableParticipation();
        var otherStudentsParticipation = new ProgrammingExerciseStudentParticipation();
        otherStudentsParticipation.setId(20L);
        otherStudentsParticipation.setRepositoryUri("https://artemis.example.com/git/ABC/abc-other.git");
        when(programmingExerciseStudentParticipationRepository.findByIdElseThrow(20L)).thenReturn(otherStudentsParticipation);
        // The student's own participation is checked first and passes, so this stub must not make that call a strict mismatch.
        org.mockito.Mockito.lenient().doThrow(new AccessForbiddenException("not yours")).when(participationAuthCheckService)
                .checkCanAccessParticipationElseThrow(otherStudentsParticipation);

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> resource.resetRepository(PARTICIPATION_ID, 20L));

        verify(programmingExerciseParticipationService, never()).resetRepository(any(), any());
    }

    @Test
    void resettingWithoutACiSystemIsReportedAsAMisconfiguration() throws Exception {
        // Nothing would ever build the reset repository, so the student would be left without any result at all.
        var resourceWithoutCi = new ProgrammingExerciseParticipationResource(programmingExerciseParticipationService, resultRepository, participationRepository,
                programmingExerciseStudentParticipationRepository, submissionService, programmingExerciseRepository, authCheckService, resultService, participationAuthCheckService,
                repositoryService, Optional.of(studentExamApi), Optional.of(vcsAccessLogRepository), auxiliaryRepositoryRepository, Optional.of(sharedQueueManagementService),
                Optional.of(examApi), Optional.empty());
        withAResettableParticipation();

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> resourceWithoutCi.resetRepository(PARTICIPATION_ID, null));
    }

    // --- the commit history of a participation ---------------------------------------------------------------------

    @Test
    void theCommitHistoryOfAParticipationIsReadFromItsRepository() throws Exception {
        when(programmingExerciseStudentParticipationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);
        var commit = new CommitInfoDTO("hash", "message", java.time.ZonedDateTime.now(), "Anna", "anna@example.com");
        when(programmingExerciseParticipationService.getCommitInfos(any(LocalVCRepositoryUri.class))).thenReturn(List.of(commit));

        var response = resource.getCommitHistoryForParticipationRepo(PARTICIPATION_ID);

        assertThat(response.getBody()).containsExactly(commit);
    }

    @Test
    void theCommitHistoryOfSomebodyElsesParticipationIsRefused() throws Exception {
        // The history shows the commit messages of the work, which is as much the student's as the code itself.
        when(programmingExerciseStudentParticipationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);
        doThrow(new AccessForbiddenException("not yours")).when(participationAuthCheckService).checkCanAccessParticipationElseThrow(participation);

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> resource.getCommitHistoryForParticipationRepo(PARTICIPATION_ID));

        verify(programmingExerciseParticipationService, never()).getCommitInfos(any());
    }

    @Test
    void theAccessLogIsRefusedOnAServerThatDoesNotKeepOne() throws Exception {
        // Without LocalVC there is no access log at all, and an empty list would look like nobody ever accessed the repository.
        var resourceWithoutLog = new ProgrammingExerciseParticipationResource(programmingExerciseParticipationService, resultRepository, participationRepository,
                programmingExerciseStudentParticipationRepository, submissionService, programmingExerciseRepository, authCheckService, resultService, participationAuthCheckService,
                repositoryService, Optional.of(studentExamApi), Optional.empty(), auxiliaryRepositoryRepository, Optional.of(sharedQueueManagementService), Optional.of(examApi),
                Optional.of(continuousIntegrationTriggerService));

        assertThat(resourceWithoutLog.getVcsAccessLogForParticipationRepo(PARTICIPATION_ID).getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void checkIfParticipationHasResult_reportsWhetherAnyResultExists() throws Exception {
        when(resultRepository.existsBySubmissionParticipationId(PARTICIPATION_ID)).thenReturn(true);

        assertThat(resource.checkIfParticipationHashResult(PARTICIPATION_ID).getBody()).isTrue();
    }

    @Test
    void checkIfParticipationHasResult_reportsFalseForAParticipationThatWasNeverBuilt() throws Exception {
        when(resultRepository.existsBySubmissionParticipationId(PARTICIPATION_ID)).thenReturn(false);

        assertThat(resource.checkIfParticipationHashResult(PARTICIPATION_ID).getBody()).isFalse();
    }
}
