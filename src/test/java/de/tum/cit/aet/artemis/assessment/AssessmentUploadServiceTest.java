package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentUploadErrorType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUploadResultDTO;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

class AssessmentUploadServiceTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "assessmentupload";

    private static final String CSV_FILE_NAME = "assessment-scores.csv";

    @Autowired
    private AssessmentUploadService assessmentUploadService;

    private ProgrammingExercise programmingExercise;

    private ProgrammingExerciseStudentParticipation participation1;

    private ProgrammingExerciseStudentParticipation participation2;

    private String identifier1;

    private String identifier2;

    private List<String> identifiers;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 6, 0, 0, 1);
        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise.setMaxPoints(100.0);
        programmingExercise.setBonusPoints(0.0);
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());

        participation1 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        participation2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        identifier1 = participation1.getId() + "-" + TEST_PREFIX + "student1";
        identifier2 = participation2.getId() + "-" + TEST_PREFIX + "student2";
        identifiers = new ArrayList<>(List.of(identifier1, identifier2));
        for (int studentNumber = 3; studentNumber <= 6; studentNumber++) {
            final var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student" + studentNumber);
            identifiers.add(participation.getId() + "-" + TEST_PREFIX + "student" + studentNumber);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldStoreScoreAndFeedbackForEveryParticipant() {
        final String csv = """
                Identifier,Name,Overall points
                %s,Student One,80
                %s,Student Two,55.5
                """.formatted(identifier1, identifier2);
        final Map<String, String> textFiles = new LinkedHashMap<>();
        // The text file is named after the full exported repository folder, i.e. it ends with the CSV identifier.
        textFiles.put("DevOps-Deployment-" + identifier1 + ".txt", "Great work, student one!");
        textFiles.put("DevOps-Deployment-" + identifier2 + ".txt", "Some issues, student two.");

        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise, buildZip(csv, textFiles));

        assertThat(result.errors()).isEmpty();
        assertThat(result.numberOfCreatedAssessments()).isEqualTo(2);
        assertThat(result.createdStudentIdentifiers()).containsExactlyInAnyOrder(identifier1, identifier2);

        assertManualAssessment(participation1.getId(), 80.0, "Great work, student one!");
        assertManualAssessment(participation2.getId(), 55.5, "Some issues, student two.");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldOverwriteAnExistingManualAssessment() {
        final Result automaticResult = participationUtilService.createSubmissionAndResult(participation1, 25, true);
        automaticResult.setAssessmentType(AssessmentType.AUTOMATIC);
        resultRepository.saveAndFlush(automaticResult);

        final Map<String, String> textFiles = new LinkedHashMap<>();
        textFiles.put(identifier1 + ".txt", "First feedback");
        assessmentUploadService.importAssessments(programmingExercise, buildZip("Identifier,Overall points\n%s,40\n".formatted(identifier1), textFiles));
        assertManualAssessment(participation1.getId(), 40.0, "First feedback");

        final Map<String, String> newTextFiles = new LinkedHashMap<>();
        newTextFiles.put(identifier1 + ".txt", "Corrected feedback");
        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Overall points\n%s,90\n".formatted(identifier1), newTextFiles));

        assertThat(result.errors()).isEmpty();
        // Still exactly one manual result, now with the corrected score and feedback.
        assertThat(getManualResults(participation1.getId())).hasSize(1);
        assertManualAssessment(participation1.getId(), 90.0, "Corrected feedback");
        assertThat(resultRepository.findById(automaticResult.getId())).isPresent().get().extracting(Result::getAssessmentType).isEqualTo(AssessmentType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectPointsAboveMaximumIncludingBonusPoints() {
        programmingExercise.setBonusPoints(10.0);
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        final Map<String, String> textFiles = Map.of(identifier1 + ".txt", "feedback");

        final AssessmentUploadResultDTO accepted = assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Overall points\n%s,110\n".formatted(identifier1), textFiles));
        final AssessmentUploadResultDTO rejected = assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Overall points\n%s,110.01\n".formatted(identifier1), textFiles));

        assertThat(accepted.errors()).isEmpty();
        assertThat(rejected.errors()).extracting(error -> error.type()).containsExactly(AssessmentUploadErrorType.INVALID_POINTS);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectDuplicateTextFileBaseNames() {
        final Map<String, String> textFiles = new LinkedHashMap<>();
        textFiles.put("first/" + identifier1 + ".txt", "first");
        textFiles.put("second/" + identifier1 + ".TXT", "second");

        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Overall points\n%s,80\n".formatted(identifier1), textFiles));

        assertThat(result.errors()).extracting(error -> error.type()).containsExactly(AssessmentUploadErrorType.DUPLICATE_TEXT_FILE);
        assertThat(getManualResults(participation1.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReportMalformedCsvSeparatelyFromEmptyCsv() {
        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Overall points\n\"unterminated,80\n", Map.of(identifier1 + ".txt", "feedback")));

        assertThat(result.errors()).extracting(error -> error.type()).containsExactly(AssessmentUploadErrorType.MALFORMED_CSV);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectZipEntryAboveUncompressedSizeLimit() {
        final String oversizedFeedback = "x".repeat(10 * 1024 * 1024 + 1);

        assertThatThrownBy(() -> assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Overall points\n%s,80\n".formatted(identifier1), Map.of(identifier1 + ".txt", oversizedFeedback))))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("maximum uncompressed size");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectWholeUploadWhenOverallPointsColumnIsMissing() {
        final String csv = "Identifier,Name\n%s,Student One\n".formatted(identifier1);
        final Map<String, String> textFiles = new LinkedHashMap<>();
        textFiles.put(identifier1 + ".txt", "feedback");

        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise, buildZip(csv, textFiles));

        assertThat(result.errors()).extracting(error -> error.type()).containsExactly(AssessmentUploadErrorType.MISSING_OVERALL_POINTS_COLUMN);
        assertThat(getManualResults(participation1.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectUploadWithUnknownParticipationAndStoreNothing() {
        final String csv = "Identifier,Overall points\n%s,80\n999999-nobody,50\n".formatted(identifier1);
        final Map<String, String> textFiles = new LinkedHashMap<>();
        textFiles.put(identifier1 + ".txt", "feedback one");
        textFiles.put("999999-nobody.txt", "feedback nobody");

        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise, buildZip(csv, textFiles));

        assertThat(result.errors()).extracting(error -> error.type()).contains(AssessmentUploadErrorType.PARTICIPATION_NOT_FOUND);
        // All-or-nothing: even the valid row must not have been stored.
        assertThat(getManualResults(participation1.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReportMissingAndUnmatchedTextFiles() {
        final String csv = "Identifier,Overall points\n%s,80\n".formatted(identifier1);
        final Map<String, String> textFiles = new LinkedHashMap<>();
        // No text file for identifier1, but an orphan text file that matches no row.
        textFiles.put("orphan-file.txt", "orphan");

        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise, buildZip(csv, textFiles));

        assertThat(result.errors()).extracting(error -> error.type()).contains(AssessmentUploadErrorType.MISSING_TEXT_FILE, AssessmentUploadErrorType.UNMATCHED_TEXT_FILE);
        assertThat(getManualResults(participation1.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectUploadWithoutCsv() {
        final Map<String, String> textFiles = new LinkedHashMap<>();
        textFiles.put(identifier1 + ".txt", "feedback");

        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise, buildZip(null, textFiles));

        assertThat(result.errors()).extracting(error -> error.type()).containsExactly(AssessmentUploadErrorType.MISSING_CSV);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectUploadWhenLoginDoesNotMatchParticipation() {
        final String csv = "Identifier,Overall points\n%s-wrong,80\n".formatted(identifier1);
        // No text file for the row: it fails the login cross-check before text-file matching, so the only expected error is the mismatch itself.
        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise, buildZip(csv, new LinkedHashMap<>()));

        assertThat(result.errors()).extracting(error -> error.type()).containsExactly(AssessmentUploadErrorType.IDENTIFIER_MISMATCH);
        assertThat(getManualResults(participation1.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldUseConstantNumberOfQueriesWhenValidatingMultipleParticipants() throws Exception {
        final AssessmentUploadResultDTO threeParticipantResult = assertThatDb(
                () -> assessmentUploadService.importAssessments(programmingExercise, buildZip(buildCsvWithoutTextFiles(identifiers.subList(0, 3)), Map.of())))
                .hasBeenCalledTimes(1);
        final AssessmentUploadResultDTO sixParticipantResult = assertThatDb(
                () -> assessmentUploadService.importAssessments(programmingExercise, buildZip(buildCsvWithoutTextFiles(identifiers), Map.of()))).hasBeenCalledTimes(1);

        assertThat(threeParticipantResult.errors()).hasSize(3).allMatch(error -> error.type() == AssessmentUploadErrorType.MISSING_TEXT_FILE);
        assertThat(sixParticipantResult.errors()).hasSize(6).allMatch(error -> error.type() == AssessmentUploadErrorType.MISSING_TEXT_FILE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectNullArguments() {
        final MockMultipartFile zip = buildZip("Identifier,Overall points\n", new LinkedHashMap<>());
        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadService.importAssessments(null, zip));
        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadService.importAssessments(programmingExercise, null));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectExerciseWithoutPositiveMaximumPoints() {
        final MockMultipartFile zip = buildZip("Identifier,Overall points\n", new LinkedHashMap<>());
        programmingExercise.setMaxPoints(0.0);

        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadService.importAssessments(programmingExercise, zip))
                .withMessage("The exercise for a manual assessment upload must have positive maximum points");
    }

    private void assertManualAssessment(final long participationId, final double expectedScore, final String expectedFeedback) {
        final List<Result> manualResults = getManualResults(participationId);
        assertThat(manualResults).hasSize(1);
        final Result result = manualResults.getFirst();
        assertThat(result.getScore()).isCloseTo(expectedScore, within(0.01));
        assertThat(result.isRated()).isTrue();
        assertThat(result.getFeedbacks()).hasSize(1);
        final Feedback feedback = result.getFeedbacks().iterator().next();
        assertThat(feedback.getType()).isEqualTo(FeedbackType.MANUAL_UNREFERENCED);
        assertThat(feedback.getDetailText()).isEqualTo(expectedFeedback);
        assertThat(feedback.getCredits()).isCloseTo(expectedScore, within(0.01));
    }

    private List<Result> getManualResults(final long participationId) {
        final var participation = studentParticipationRepository.findWithEagerSubmissionsResultsFeedbacksById(participationId).orElseThrow();
        return participation.getSubmissions().stream().flatMap(submission -> submission.getResults().stream()).filter(Result::isManual).toList();
    }

    private MockMultipartFile buildZip(final String csvContent, final Map<String, String> textFilesByName) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            if (csvContent != null) {
                zipOutputStream.putNextEntry(new ZipEntry(CSV_FILE_NAME));
                zipOutputStream.write(csvContent.getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
            for (final Map.Entry<String, String> textFile : textFilesByName.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(textFile.getKey()));
                zipOutputStream.write(textFile.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return new MockMultipartFile("file", "assessments.zip", "application/zip", byteArrayOutputStream.toByteArray());
    }

    private String buildCsvWithoutTextFiles(final List<String> participantIdentifiers) {
        return "Identifier,Overall points\n" + participantIdentifiers.stream().map(identifier -> identifier + ",80").collect(java.util.stream.Collectors.joining("\n")) + "\n";
    }
}
