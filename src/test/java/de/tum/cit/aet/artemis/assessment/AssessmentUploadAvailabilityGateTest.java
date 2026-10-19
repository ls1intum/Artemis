package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
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

/**
 * The shared assessment-availability gate only distinguishes test runs from everything else; for an exam it then answers the same exercise-wide question by reading the latest
 * individual working time. Asking it per participation would repeat that aggregate query once per uploaded assessment, and the upload holds the exercise lock while it does.
 */
class AssessmentUploadAvailabilityGateTest {

    private static final long EXERCISE_ID = 7L;

    private static final int PARTICIPANT_COUNT = 5;

    @Test
    void shouldAskTheAvailabilityGateOnceForTheWholeBatch() {
        final SubmissionService submissionService = mock(SubmissionService.class);
        final List<StudentParticipation> participations = participations(false);

        importFor(participations, submissionService);

        verify(submissionService, times(1)).checkThatAssessmentIsPossibleElseThrow(any(), any());
    }

    @Test
    void shouldAskTheAvailabilityGateWithAParticipationThatIsNotATestRun() {
        final SubmissionService submissionService = mock(SubmissionService.class);
        // A test run is exempt from the gate, so asking with one would pass a batch the gate should have rejected.
        final List<StudentParticipation> participations = participations(false);
        participations.getFirst().setTestRun(true);

        importFor(participations, submissionService);

        final var asked = org.mockito.ArgumentCaptor.<StudentParticipation>captor();
        verify(submissionService).checkThatAssessmentIsPossibleElseThrow(any(), asked.capture());
        assertThat(asked.getValue().isTestRun()).isFalse();
    }

    @Test
    void shouldNotAskTheAvailabilityGateForABatchOfTestRunsOnly() {
        final SubmissionService submissionService = mock(SubmissionService.class);
        final List<StudentParticipation> participations = participations(true);

        importFor(participations, submissionService);

        verify(submissionService, never()).checkThatAssessmentIsPossibleElseThrow(any(), any());
    }

    private void importFor(final List<StudentParticipation> participations, final SubmissionService submissionService) {
        final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
            final Submission submission = invocation.getArgument(0);
            submission.setId(900L + submission.getParticipation().getId());
            return submission;
        });
        final AssessmentUploadResultService resultService = mock(AssessmentUploadResultService.class);
        when(resultService.findParticipationsWithComplaintOnResults(any())).thenReturn(Set.of());

        final AssessmentUploadParticipationRepository participationRepository = mock(AssessmentUploadParticipationRepository.class);
        when(participationRepository.findAssessmentUploadParticipations(eq(EXERCISE_ID), any()))
                .thenReturn(participations.stream().map(p -> new AssessmentUploadParticipationDTO(p.getId(), login(p.getId()))).toList());
        when(participationRepository.findIdsOutsideExercise(eq(EXERCISE_ID), any())).thenReturn(Set.of());
        when(participationRepository.findAllForAssessmentUpload(eq(EXERCISE_ID), any())).thenReturn(participations);

        final AssessmentUploadService service = new AssessmentUploadService(archiveParserFor(participations), participationRepository, submissionRepository, resultService,
                submissionService);
        service.importAssessments(exercise(), new MockMultipartFile("file", "upload.zip", "application/zip", new byte[] { 1 }));
    }

    private List<StudentParticipation> participations(final boolean testRun) {
        return IntStream.rangeClosed(1, PARTICIPANT_COUNT).mapToObj(index -> {
            final StudentParticipation participation = new StudentParticipation();
            participation.setId((long) index);
            participation.setTestRun(testRun);
            final User student = new User();
            student.setLogin(login((long) index));
            participation.setParticipant(student);
            return participation;
        }).collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    private AssessmentUploadArchiveParsingService archiveParserFor(final List<StudentParticipation> participations) {
        final List<String> identifiers = participations.stream().map(p -> p.getId() + "-" + login(p.getId())).toList();
        final Map<String, String> textFiles = identifiers.stream().collect(Collectors.toMap(identifier -> identifier, identifier -> "well done"));
        final String csv = "Identifier,Overall points\n" + identifiers.stream().map(identifier -> identifier + ",80\n").collect(Collectors.joining());
        final AssessmentUploadArchiveParsingService archiveParser = mock(AssessmentUploadArchiveParsingService.class);
        when(archiveParser.readZipContents(any())).thenReturn(new ZipContents(List.of(new byte[0]), textFiles, Set.of()));
        when(archiveParser.parseCsv(any())).thenReturn(new ParsedCsv(csvRecords(csv), "Overall points"));
        return archiveParser;
    }

    private String login(final long participationId) {
        return "student" + participationId;
    }

    private ProgrammingExercise exercise() {
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
