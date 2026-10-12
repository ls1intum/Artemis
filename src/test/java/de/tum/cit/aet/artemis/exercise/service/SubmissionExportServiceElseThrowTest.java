package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.service.TextSubmissionExportService;

/**
 * Tests that {@link SubmissionExportService#exportStudentSubmissionsElseThrow} tells an empty export (nothing to export) apart from
 * a failed export (there were submissions, but writing the export files failed). Both used to be reported as the 400 "noSubmissions".
 */
class SubmissionExportServiceElseThrowTest {

    private static final long EXERCISE_ID = 161L;

    @TempDir
    private Path tempDir;

    private ExerciseTestRepository exerciseRepository;

    private ZipFileService zipFileService;

    private FileService fileService;

    private TextExercise exercise;

    private StudentParticipation participation;

    private TextSubmissionExportService service;

    @BeforeEach
    void setUp() throws IOException {
        exerciseRepository = mock(ExerciseTestRepository.class);
        zipFileService = mock(ZipFileService.class);
        fileService = mock(FileService.class);
        service = new TextSubmissionExportService(exerciseRepository, zipFileService, fileService);
        ReflectionTestUtils.setField(service, "submissionExportPath", tempDir);

        Course course = new Course();
        course.setShortName("course");
        exercise = new TextExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setTitle("textex10");
        exercise.setCourse(course);

        User student = new User();
        student.setLogin("user_4");
        participation = new StudentParticipation();
        participation.setId(1L);
        participation.setParticipant(student);
        participation.setExercise(exercise);
        exercise.setStudentParticipations(Set.of(participation));

        when(exerciseRepository.findWithEagerStudentParticipationsStudentAndSubmissionsById(EXERCISE_ID)).thenReturn(Optional.of(exercise));
        when(fileService.createTemporaryDirectory(eq(tempDir), anyString(), anyLong())).thenReturn(tempDir);
    }

    private void addSubmittedSubmission() {
        TextSubmission submission = new TextSubmission();
        submission.setId(10L);
        submission.setText("my solution");
        submission.setSubmissionDate(ZonedDateTime.now().minusDays(1));
        participation.setSubmissions(Set.of(submission));
    }

    @Test
    void exportStudentSubmissionsElseThrow_submittedSubmissions_returnsZipFile() throws IOException {
        addSubmittedSubmission();

        Path zipFile = service.exportStudentSubmissionsElseThrow(EXERCISE_ID, SubmissionExportOptionsDTO.exportAll());

        assertThat(zipFile.getParent()).isEqualTo(tempDir);
        assertThat(zipFile.getFileName().toString()).endsWith(".zip");
    }

    @Test
    void exportStudentSubmissionsElseThrow_noSubmittedSubmission_throwsBadRequestNoSubmissions() {
        participation.setSubmissions(Set.of());

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> service.exportStudentSubmissionsElseThrow(EXERCISE_ID, SubmissionExportOptionsDTO.exportAll()))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("noSubmissions"));
    }

    @Test
    void exportStudentSubmissionsElseThrow_participantListMatchesNobody_throwsBadRequestNoSubmissions() {
        addSubmittedSubmission();

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> service.exportStudentSubmissionsElseThrow(EXERCISE_ID, new SubmissionExportOptionsDTO(false, false, null, "someone_else")))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("noSubmissions"));
    }

    @Test
    void exportStudentSubmissionsElseThrow_submissionFileCannotBeWritten_throwsInternalServerErrorInsteadOfNoSubmissions() throws IOException {
        addSubmittedSubmission();
        Path missingDirectory = tempDir.resolve("does-not-exist");
        when(fileService.createTemporaryDirectory(eq(tempDir), anyString(), anyLong())).thenReturn(missingDirectory);

        assertThatExceptionOfType(InternalServerErrorException.class)
                .isThrownBy(() -> service.exportStudentSubmissionsElseThrow(EXERCISE_ID, SubmissionExportOptionsDTO.exportAll()))
                .satisfies(exception -> assertThat(exception.getBody().getTitle()).contains("Failed to export the submissions of exercise " + EXERCISE_ID)
                        .doesNotContain(tempDir.toString()));
    }

    @Test
    void exportStudentSubmissionsElseThrow_zipFileCannotBeCreated_throwsInternalServerErrorInsteadOfNoSubmissions() throws IOException {
        addSubmittedSubmission();
        doThrow(new IOException("disk full")).when(zipFileService).createZipFile(any(Path.class), anyList());

        assertThatExceptionOfType(InternalServerErrorException.class)
                .isThrownBy(() -> service.exportStudentSubmissionsElseThrow(EXERCISE_ID, SubmissionExportOptionsDTO.exportAll()))
                .satisfies(exception -> assertThat(exception.getBody().getTitle()).contains("Failed to export the submissions of exercise " + EXERCISE_ID)
                        .doesNotContain("disk full"));
    }

    @Test
    void exportStudentSubmissionsElseThrow_exportDirectoryCannotBeCreated_throwsInternalServerError() throws IOException {
        addSubmittedSubmission();
        when(fileService.createTemporaryDirectory(eq(tempDir), anyString(), anyLong())).thenThrow(new IOException("read-only file system"));

        assertThatExceptionOfType(InternalServerErrorException.class)
                .isThrownBy(() -> service.exportStudentSubmissionsElseThrow(EXERCISE_ID, SubmissionExportOptionsDTO.exportAll()))
                .satisfies(exception -> assertThat(exception.getBody().getTitle()).contains("Failed to create the export directory for exercise " + EXERCISE_ID));
    }
}
