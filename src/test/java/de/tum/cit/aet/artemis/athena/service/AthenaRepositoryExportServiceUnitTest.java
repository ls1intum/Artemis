package de.tum.cit.aet.artemis.athena.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;

@ExtendWith(MockitoExtension.class)
class AthenaRepositoryExportServiceUnitTest {

    private static final long EXERCISE_ID = 27L;

    private static final long SUBMISSION_ID = 41L;

    private static final long PARTICIPATION_ID = 99L;

    /** A fixed due date that has passed for the participations of the tests; the clock is not what they check. */
    private static final ZonedDateTime PAST_DUE_DATE = ZonedDateTime.parse("2026-01-15T12:00:00Z");

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Mock
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    @Mock
    private CourseAthenaConfigRepository courseAthenaConfigRepository;

    private AthenaRepositoryExportService athenaRepositoryExportService;

    private ProgrammingExercise programmingExercise;

    private ProgrammingSubmission programmingSubmission;

    private ProgrammingExerciseStudentParticipation participation;

    @BeforeEach
    void setUp() {
        athenaRepositoryExportService = new AthenaRepositoryExportService(programmingExerciseRepository, repositoryService, programmingSubmissionRepository,
                programmingExerciseStudentParticipationRepository, courseAthenaConfigRepository);

        programmingExercise = new ProgrammingExercise();
        programmingExercise.setId(EXERCISE_ID);
        // Graded Athena feedback (and thus its repository export) is only offered for manually assessed programming
        // exercises; automatically assessed ones rely on unit-test feedback.
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        var course = new Course();
        var athenaConfig = new CourseAthenaConfig();
        athenaConfig.setGradingFeedbackEnabled(true);
        course.setAthenaConfig(athenaConfig);
        programmingExercise.setCourse(course);

        programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setId(SUBMISSION_ID);

        participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(PARTICIPATION_ID);
        programmingSubmission.setParticipation(participation);
    }

    @Test
    void getStudentRepositoryFilesContentShouldCheckFeedbackSettings() {
        programmingExercise.setCourse(null); // disable Athena: no course → both areFeedbackSuggestionsEnabled() and getAllowFeedbackRequests() return false

        when(programmingExerciseRepository.findByIdElseThrow(EXERCISE_ID)).thenReturn(programmingExercise);

        assertThatExceptionOfType(ServiceUnavailableException.class).isThrownBy(() -> athenaRepositoryExportService.getStudentRepositoryFilesContent(EXERCISE_ID, SUBMISSION_ID))
                .withMessageContaining("Feedback suggestions are not enabled");
    }

    @Test
    void getStudentRepositoryFilesContentShouldThrowWhenSubmissionDoesNotBelongToExercise() {
        long wrongExerciseId = 999L;
        ProgrammingExercise wrongExercise = new ProgrammingExercise();
        wrongExercise.setId(wrongExerciseId);
        participation.setExercise(wrongExercise);

        when(programmingExerciseRepository.findByIdElseThrow(EXERCISE_ID)).thenReturn(programmingExercise);
        when(programmingSubmissionRepository.findByIdElseThrow(SUBMISSION_ID)).thenReturn(programmingSubmission);
        when(programmingExerciseStudentParticipationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> athenaRepositoryExportService.getStudentRepositoryFilesContent(EXERCISE_ID, SUBMISSION_ID))
                .withMessageContaining("Submission " + SUBMISSION_ID + " does not belong to exercise " + EXERCISE_ID)
                .withMessageContaining("belongs to exercise " + wrongExerciseId)
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("submissionExerciseMismatch"));
    }

    @Test
    void getStudentRepositoryFilesContentShouldThrowWhenRepositoryUriMissing() {
        participation.setExercise(programmingExercise);

        when(programmingExerciseRepository.findByIdElseThrow(EXERCISE_ID)).thenReturn(programmingExercise);
        when(programmingSubmissionRepository.findByIdElseThrow(SUBMISSION_ID)).thenReturn(programmingSubmission);
        when(programmingExerciseStudentParticipationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> athenaRepositoryExportService.getStudentRepositoryFilesContent(EXERCISE_ID, SUBMISSION_ID))
                .withMessageContaining("Repository URI is null");
    }

    @Test
    void getStudentRepositoryFilesContentShouldUseLatestCommitWhenNoDeadline() throws IOException {
        participation.setExercise(programmingExercise);
        participation.setRepositoryUri(new LocalVCRepositoryUri(URI.create("http://localhost"), "TEST", "TEST-student").getURI().toString());

        when(programmingExerciseRepository.findByIdElseThrow(EXERCISE_ID)).thenReturn(programmingExercise);
        when(programmingSubmissionRepository.findByIdElseThrow(SUBMISSION_ID)).thenReturn(programmingSubmission);
        when(programmingExerciseStudentParticipationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);

        var expected = Map.of("Test.java", "class Test {}");
        when(repositoryService.getFilesContentFromBareRepositoryForLastCommit(participation.getVcsRepositoryUri())).thenReturn(expected);

        Map<String, String> filesContent = athenaRepositoryExportService.getStudentRepositoryFilesContent(EXERCISE_ID, SUBMISSION_ID);

        assertThat(filesContent).isEqualTo(expected);
        verify(repositoryService).getFilesContentFromBareRepositoryForLastCommit(participation.getVcsRepositoryUri());
    }

    @Test
    void getStudentRepositoryFilesContentShouldUseLatestCommitForPracticeParticipationAfterDueDate() throws IOException {
        programmingExercise.setDueDate(PAST_DUE_DATE);
        participation.setExercise(programmingExercise);
        participation.setPracticeMode(true);
        participation.setRepositoryUri(new LocalVCRepositoryUri(URI.create("http://localhost"), "TEST", "TEST-practice-student").getURI().toString());

        when(programmingExerciseRepository.findByIdElseThrow(EXERCISE_ID)).thenReturn(programmingExercise);
        when(programmingSubmissionRepository.findByIdElseThrow(SUBMISSION_ID)).thenReturn(programmingSubmission);
        when(programmingExerciseStudentParticipationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);

        var expected = Map.of("Test.java", "class Test {}");
        when(repositoryService.getFilesContentFromBareRepositoryForLastCommit(participation.getVcsRepositoryUri())).thenReturn(expected);

        Map<String, String> filesContent = athenaRepositoryExportService.getStudentRepositoryFilesContent(EXERCISE_ID, SUBMISSION_ID);

        assertThat(filesContent).isEqualTo(expected);
        verify(repositoryService).getFilesContentFromBareRepositoryForLastCommit(participation.getVcsRepositoryUri());
        verify(repositoryService, never()).getFilesContentFromBareRepositoryForLastCommitBeforeOrAt(any(LocalVCRepositoryUri.class), any(ZonedDateTime.class));
    }

    @Test
    void getStudentRepositoryFilesContentShouldUseLastCommitBeforeDueDateForGradedParticipation() throws IOException {
        programmingExercise.setDueDate(PAST_DUE_DATE);
        participation.setExercise(programmingExercise);
        participation.setRepositoryUri(new LocalVCRepositoryUri(URI.create("http://localhost"), "TEST", "TEST-student").getURI().toString());

        when(programmingExerciseRepository.findByIdElseThrow(EXERCISE_ID)).thenReturn(programmingExercise);
        when(programmingSubmissionRepository.findByIdElseThrow(SUBMISSION_ID)).thenReturn(programmingSubmission);
        when(programmingExerciseStudentParticipationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);

        var expected = Map.of("Test.java", "class Test {}");
        when(repositoryService.getFilesContentFromBareRepositoryForLastCommitBeforeOrAt(participation.getVcsRepositoryUri(), PAST_DUE_DATE)).thenReturn(expected);

        Map<String, String> filesContent = athenaRepositoryExportService.getStudentRepositoryFilesContent(EXERCISE_ID, SUBMISSION_ID);

        assertThat(filesContent).isEqualTo(expected);
        verify(repositoryService, never()).getFilesContentFromBareRepositoryForLastCommit(any(LocalVCRepositoryUri.class));
    }

    @Test
    void getInstructorRepositoryFilesContentShouldValidateRepositoryUri() {
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(EXERCISE_ID)).thenReturn(programmingExercise);

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> athenaRepositoryExportService.getInstructorRepositoryFilesContent(EXERCISE_ID, RepositoryType.SOLUTION))
                .withMessageContaining("Repository URI is null for exercise " + EXERCISE_ID);
    }
}
