package de.tum.cit.aet.artemis.athena.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;

@ExtendWith(MockitoExtension.class)
class AthenaRepositoryExportServiceUnitTest {

    private static final long EXERCISE_ID = 27L;

    private static final long SUBMISSION_ID = 41L;

    private static final long PARTICIPATION_ID = 99L;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Mock
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    private AthenaRepositoryExportService athenaRepositoryExportService;

    private ProgrammingExercise programmingExercise;

    private ProgrammingSubmission programmingSubmission;

    private ProgrammingExerciseStudentParticipation participation;

    @BeforeEach
    void setUp() {
        athenaRepositoryExportService = new AthenaRepositoryExportService(programmingExerciseRepository, repositoryService, programmingSubmissionRepository,
                programmingExerciseStudentParticipationRepository);

        programmingExercise = new ProgrammingExercise();
        programmingExercise.setId(EXERCISE_ID);

        programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setId(SUBMISSION_ID);

        participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(PARTICIPATION_ID);
        programmingSubmission.setParticipation(participation);
    }

    @Test
    void getStudentRepositoryFilesContentShouldThrowWhenRepositoryUriMissing() {
        when(programmingExerciseRepository.findByIdElseThrow(EXERCISE_ID)).thenReturn(programmingExercise);
        when(programmingSubmissionRepository.findByIdElseThrow(SUBMISSION_ID)).thenReturn(programmingSubmission);
        when(programmingExerciseStudentParticipationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> athenaRepositoryExportService.getStudentRepositoryFilesContent(EXERCISE_ID, SUBMISSION_ID))
                .withMessageContaining("Repository URI is null");
    }

    @Test
    void getStudentRepositoryFilesContentShouldUseLatestCommitWhenNoDeadline() throws IOException {
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
    void getInstructorRepositoryFilesContentShouldValidateRepositoryUri() {
        programmingExercise.setFeedbackSuggestionModule("module");
        programmingExercise.setAllowFeedbackRequests(false);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(EXERCISE_ID)).thenReturn(programmingExercise);

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> athenaRepositoryExportService.getInstructorRepositoryFilesContent(EXERCISE_ID, RepositoryType.SOLUTION))
                .withMessageContaining("Repository URI is null for exercise " + EXERCISE_ID);
    }
}
