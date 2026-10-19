package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUploadParticipationDTO;
import de.tum.cit.aet.artemis.assessment.repository.AssessmentUploadParticipationRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadArchiveParsingService;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadArchiveParsingService.ParsedCsv;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadArchiveParsingService.ZipContents;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadResultService;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/**
 * Covers the compensation that replaces the transaction the storage path is not allowed to open: a submission created for a participant who had none is committed on its own,
 * so a later failure has to take it back rather than leave a submitted submission without an assessment behind.
 */
class AssessmentUploadFailureCompensationTest {

    private static final long EXERCISE_ID = 7L;

    private static final long PARTICIPATION_ID = 42L;

    private static final String LOGIN = "student1";

    private static final String IDENTIFIER = PARTICIPATION_ID + "-" + LOGIN;

    @Test
    void shouldRemoveTheSubmissionItCreatedWhenStoringTheAssessmentsFails() {
        final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        // Mirror what the repository does: the submission the service built, carrying its participation, comes back with an id.
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
            final Submission submission = invocation.getArgument(0);
            submission.setId(815L);
            return submission;
        });

        final AssessmentUploadResultService resultService = mock(AssessmentUploadResultService.class);
        when(resultService.findParticipationsWithComplaintOnResults(any())).thenReturn(Set.of());
        final RuntimeException storageFailure = new IllegalStateException("the result write failed");
        // Reproduce a batch that rolled back after it had already inserted a row: the result keeps the id the database generated for it even though the row is gone again.
        when(resultService.saveManualResults(any(), any(), anyBoolean())).thenAnswer(invocation -> {
            final Collection<Result> newResults = invocation.getArgument(0);
            newResults.forEach(result -> result.setId(4711L));
            throw storageFailure;
        });

        final AssessmentUploadService service = new AssessmentUploadService(archiveParserReturningOneRow(), participationRepositoryReturningOneParticipation(),
                submissionRepository, resultService, mock(SubmissionService.class));

        assertThatThrownBy(() -> service.importAssessments(exerciseAllowingManualResults(), new MockMultipartFile("file", "upload.zip", "application/zip", new byte[] { 1 })))
                .isSameAs(storageFailure);

        // Deleted by id, so the submission is reloaded instead of merged. Handing back the instance would cascade a merge to the result above, whose id no longer matches any
        // row, and the cleanup would fail on that rather than remove the submission.
        final ArgumentCaptor<Iterable<Long>> deleted = ArgumentCaptor.captor();
        verify(submissionRepository).deleteAllById(deleted.capture());
        assertThat(deleted.getValue()).containsExactly(815L);
        verify(submissionRepository, never()).deleteAll(any());
    }

    @Test
    void shouldNotDeleteAnythingWhenEveryParticipantAlreadyHasASubmission() {
        final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);

        final AssessmentUploadResultService resultService = mock(AssessmentUploadResultService.class);
        when(resultService.findParticipationsWithComplaintOnResults(any())).thenReturn(Set.of());
        final RuntimeException storageFailure = new IllegalStateException("the result write failed");
        when(resultService.saveManualResults(any(), any(), anyBoolean())).thenThrow(storageFailure);

        final StudentParticipation participation = participationWithExistingSubmission();
        final AssessmentUploadParticipationRepository participationRepository = mock(AssessmentUploadParticipationRepository.class);
        when(participationRepository.findAssessmentUploadParticipations(any(Long.class), any())).thenReturn(List.of(new AssessmentUploadParticipationDTO(PARTICIPATION_ID, LOGIN)));
        when(participationRepository.findIdsOutsideExercise(any(Long.class), any())).thenReturn(Set.of());
        when(participationRepository.findAllForAssessmentUpload(any(Long.class), any())).thenReturn(List.of(participation));

        final AssessmentUploadService service = new AssessmentUploadService(archiveParserReturningOneRow(), participationRepository, submissionRepository, resultService,
                mock(SubmissionService.class));

        assertThatThrownBy(() -> service.importAssessments(exerciseAllowingManualResults(), new MockMultipartFile("file", "upload.zip", "application/zip", new byte[] { 1 })))
                .isSameAs(storageFailure);

        // Nothing was created, so there is nothing to take back: the participant's own submission must survive the failed upload.
        verify(submissionRepository, never()).deleteAllById(any());
        verify(submissionRepository, never()).deleteAll(any());
        verify(submissionRepository, never()).save(any(Submission.class));
    }

    private AssessmentUploadArchiveParsingService archiveParserReturningOneRow() {
        final AssessmentUploadArchiveParsingService archiveParser = mock(AssessmentUploadArchiveParsingService.class);
        when(archiveParser.readZipContents(any())).thenReturn(new ZipContents(List.of(new byte[0]), Map.of(IDENTIFIER, "well done"), Set.of()));
        when(archiveParser.parseCsv(any())).thenReturn(new ParsedCsv(csvRecords("Identifier,Overall points\n%s,80\n".formatted(IDENTIFIER)), "Overall points"));
        return archiveParser;
    }

    private AssessmentUploadParticipationRepository participationRepositoryReturningOneParticipation() {
        final AssessmentUploadParticipationRepository participationRepository = mock(AssessmentUploadParticipationRepository.class);
        when(participationRepository.findAssessmentUploadParticipations(any(Long.class), any())).thenReturn(List.of(new AssessmentUploadParticipationDTO(PARTICIPATION_ID, LOGIN)));
        when(participationRepository.findIdsOutsideExercise(any(Long.class), any())).thenReturn(Set.of());
        when(participationRepository.findAllForAssessmentUpload(any(Long.class), any())).thenReturn(List.of(participationWithoutSubmission()));
        return participationRepository;
    }

    private StudentParticipation participationWithoutSubmission() {
        final StudentParticipation participation = new StudentParticipation();
        participation.setId(PARTICIPATION_ID);
        final User student = new User();
        student.setLogin(LOGIN);
        participation.setParticipant(student);
        return participation;
    }

    private StudentParticipation participationWithExistingSubmission() {
        final StudentParticipation participation = participationWithoutSubmission();
        final Submission submission = new ProgrammingSubmission();
        submission.setId(99L);
        submission.setParticipation(participation);
        participation.addSubmission(submission);
        return participation;
    }

    private ProgrammingExercise exerciseAllowingManualResults() {
        final ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setCourse(new Course());
        exercise.setMaxPoints(100.0);
        exercise.setBonusPoints(0.0);
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setReleaseDate(ZonedDateTime.now().minusDays(7));
        exercise.setDueDate(ZonedDateTime.now().minusDays(3));
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        exercise.setAssessmentDueDate(ZonedDateTime.now().minusDays(1));
        return exercise;
    }

    private List<CSVRecord> csvRecords(final String csv) {
        final CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get();
        try (CSVParser parser = CSVParser.parse(new StringReader(csv), format)) {
            return parser.getRecords();
        }
        catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
