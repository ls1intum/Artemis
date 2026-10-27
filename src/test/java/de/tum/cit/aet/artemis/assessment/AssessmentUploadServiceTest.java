package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentUploadErrorType;
import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUploadResultDTO;
import de.tum.cit.aet.artemis.assessment.repository.RatingRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadService;
import de.tum.cit.aet.artemis.assessment.util.AssessmentUploadResultTestService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

class AssessmentUploadServiceTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "assessmentupload";

    private static final String CSV_FILE_NAME = "assessment-scores.csv";

    @Autowired
    private AssessmentUploadService assessmentUploadService;

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private AssessmentUploadResultTestService assessmentUploadResultTestService;

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
        // Configure the exercise so manual results are allowed (same gate as the assessment editor): semi-automatic assessment with the relevant due dates in the past.
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusDays(7));
        programmingExercise.setDueDate(ZonedDateTime.now().minusDays(3));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExercise.setAssessmentDueDate(ZonedDateTime.now().minusDays(1));
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
                Identifier,Name,Manual points
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
    void shouldGenerateATemplateThatRoundTripsThroughTheUpload() throws IOException {
        final Map<String, String> templateEntries = readZipEntries(assessmentUploadService.generateTemplateArchive(programmingExercise));

        // The template contains the CSV the parser expects (identifier in the first column, an "Manual points" column) and one feedback file per participation, each named after
        // the
        // path-safe participation id so the upload matches it back by id.
        final String templateCsv = templateEntries.get(CSV_FILE_NAME);
        assertThat(templateCsv).isNotNull();
        assertThat(templateCsv.lines().findFirst().orElseThrow()).contains("Manual points");
        assertThat(templateCsv).contains(identifier1).contains(identifier2);
        final List<String> textFileNames = templateEntries.keySet().stream().filter(name -> name.endsWith(".txt")).toList();
        assertThat(textFileNames).hasSize(identifiers.size()).contains(participation1.getId() + ".txt", participation2.getId() + ".txt");

        // Fill in points for every row and non-empty feedback for every text file, then upload the generated archive; a valid template must be accepted unchanged otherwise.
        final Map<String, String> feedbackFiles = new LinkedHashMap<>();
        for (final String textFileName : textFileNames) {
            feedbackFiles.put(textFileName, "feedback");
        }
        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise, buildZip(fillTemplatePoints(templateCsv, 75.0), feedbackFiles));

        assertThat(result.errors()).isEmpty();
        assertThat(result.numberOfCreatedAssessments()).isEqualTo(identifiers.size());
        assertManualAssessment(participation1.getId(), 75.0, "feedback");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldMatchFeedbackFileNamedAfterParticipationId() {
        // The generated template names each feedback file after the path-safe participation id (the identifier's login/team-short-name part may contain a '/'); the importer must
        // resolve such a flat name back to its CSV row.
        final String csv = "Identifier,Manual points\n%s,80\n".formatted(identifier1);
        final Map<String, String> textFiles = new LinkedHashMap<>();
        textFiles.put(participation1.getId() + ".txt", "Great work!");

        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise, buildZip(csv, textFiles));

        assertThat(result.errors()).isEmpty();
        assertThat(result.numberOfCreatedAssessments()).isEqualTo(1);
        assertManualAssessment(participation1.getId(), 80.0, "Great work!");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldMarkNewlyCreatedSubmissionAsSubmittedForParticipantWithoutSubmission() {
        // Regression test: a participant who never pushed a submission must receive a submitted, dated EXTERNAL submission. Otherwise the imported assessment is left on an
        // unsubmitted, undated submission and is omitted from finished-assessment queries and result views that require submission.submitted = TRUE.
        assertThat(getSubmissions(participation1.getId())).isEmpty();

        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,80\n".formatted(identifier1), Map.of(identifier1 + ".txt", "feedback")));

        assertThat(result.errors()).isEmpty();
        final List<Submission> submissions = getSubmissions(participation1.getId());
        assertThat(submissions).singleElement().satisfies(submission -> {
            assertThat(submission.getType()).isEqualTo(SubmissionType.EXTERNAL);
            assertThat(submission.isSubmitted()).isTrue();
            assertThat(submission.getSubmissionDate()).isNotNull();
            assertThat(submission.getSubmissionDate().toInstant()).isCloseTo(ZonedDateTime.now().toInstant(), within(1, ChronoUnit.MINUTES));
        });
        assertManualAssessment(participation1.getId(), 80.0, "feedback");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldOverwriteAnExistingManualAssessment() {
        final Result automaticResult = participationUtilService.createSubmissionAndResult(participation1, 25, true);
        automaticResult.setAssessmentType(AssessmentType.AUTOMATIC);
        resultRepository.saveAndFlush(automaticResult);

        final Map<String, String> textFiles = new LinkedHashMap<>();
        textFiles.put(identifier1 + ".txt", "First feedback");
        assessmentUploadService.importAssessments(programmingExercise, buildZip("Identifier,Manual points\n%s,40\n".formatted(identifier1), textFiles));
        assertManualAssessment(participation1.getId(), 40.0, "First feedback");
        final long firstManualResultId = getManualResults(participation1.getId()).getFirst().getId();

        final Map<String, String> newTextFiles = new LinkedHashMap<>();
        newTextFiles.put(identifier1 + ".txt", "Corrected feedback");
        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,90\n".formatted(identifier1), newTextFiles));

        assertThat(result.errors()).isEmpty();
        // Still exactly one manual result, now with the corrected score and feedback.
        assertThat(getManualResults(participation1.getId())).hasSize(1);
        assertManualAssessment(participation1.getId(), 90.0, "Corrected feedback");
        // The existing assessment is edited in place instead of being deleted and re-created, so its identity survives the overwrite.
        assertThat(getManualResults(participation1.getId()).getFirst().getId()).isEqualTo(firstManualResultId);
        assertThat(resultRepository.findById(automaticResult.getId())).isPresent().get().extracting(Result::getAssessmentType).isEqualTo(AssessmentType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReplaceExistingSemiAutomaticAssessment() {
        // A manual assessment created in the assessment editor is stored as SEMI_AUTOMATIC. The upload must replace it instead of leaving a second manual result behind.
        final Result semiAutomaticResult = participationUtilService.createSubmissionAndResult(participation1, 30, true);
        semiAutomaticResult.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        resultRepository.saveAndFlush(semiAutomaticResult);
        assertThat(getManualResults(participation1.getId())).hasSize(1);

        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,75\n".formatted(identifier1), Map.of(identifier1 + ".txt", "uploaded feedback")));

        assertThat(result.errors()).isEmpty();
        // Exactly one manual result remains: the existing SEMI_AUTOMATIC one, overwritten in place with the uploaded MANUAL assessment instead of being duplicated.
        final List<Result> manualResults = getManualResults(participation1.getId());
        assertThat(manualResults).hasSize(1);
        assertThat(manualResults.getFirst().getId()).isEqualTo(semiAutomaticResult.getId());
        assertThat(manualResults.getFirst().getAssessmentType()).isEqualTo(AssessmentType.MANUAL);
        assertManualAssessment(participation1.getId(), 75.0, "uploaded feedback");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldKeepTheRatingOfAnOverwrittenManualAssessment() {
        // The upload overwrites an existing manual assessment in place instead of deleting and re-creating it, so a student's rating of that assessment is neither destroyed nor
        // left dangling.
        assessmentUploadService.importAssessments(programmingExercise, buildZip("Identifier,Manual points\n%s,40\n".formatted(identifier1), Map.of(identifier1 + ".txt", "first")));
        final Result ratedManualResult = getManualResults(participation1.getId()).getFirst();
        participationUtilService.addRatingToResult(ratedManualResult, 3);
        assertThat(ratingRepository.findRatingByResultId(ratedManualResult.getId())).isPresent();

        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,90\n".formatted(identifier1), Map.of(identifier1 + ".txt", "second")));

        assertThat(result.errors()).isEmpty();
        // Exactly one manual result remains, it is still the same one, and the rating that referenced it survived the overwrite.
        assertManualAssessment(participation1.getId(), 90.0, "second");
        assertThat(getManualResults(participation1.getId())).singleElement().extracting(Result::getId).isEqualTo(ratedManualResult.getId());
        assertThat(ratingRepository.findRatingByResultId(ratedManualResult.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldDeleteManualResultAndAllItsDependentRows() {
        // Deleting a manual result must remove it together with every row that references it — feedback, rating, complaint and complaint response — in foreign-key-safe order; a
        // wrong order would fail with a foreign-key violation.
        assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,40\n".formatted(identifier1), Map.of(identifier1 + ".txt", "feedback to delete")));
        final Result manualResult = getManualResults(participation1.getId()).getFirst();
        participationUtilService.addRatingToResult(manualResult, 3);
        final Complaint complaint = complaintRepo.save(new Complaint().result(manualResult).complaintType(ComplaintType.COMPLAINT));
        complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "instructor1", complaint);
        assertThat(ratingRepository.findRatingByResultId(manualResult.getId())).isPresent();
        assertThat(complaintRepo.findByResultId(manualResult.getId())).isPresent();

        assessmentUploadResultTestService.deleteManualResults(programmingExercise.getId(), List.of(participation1.getId()));

        // The result and every row that referenced it are gone.
        assertThat(resultRepository.findById(manualResult.getId())).isEmpty();
        assertThat(getManualResults(participation1.getId())).isEmpty();
        assertThat(ratingRepository.findRatingByResultId(manualResult.getId())).isEmpty();
        assertThat(complaintRepo.findByResultId(manualResult.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectUploadWhenExistingAssessmentHasComplaint() {
        assessmentUploadService.importAssessments(programmingExercise, buildZip("Identifier,Manual points\n%s,40\n".formatted(identifier1), Map.of(identifier1 + ".txt", "first")));
        final Result manualResult = getManualResults(participation1.getId()).getFirst();
        complaintRepo.save(new Complaint().result(manualResult).complaintType(ComplaintType.COMPLAINT));

        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,90\n".formatted(identifier1), Map.of(identifier1 + ".txt", "second")));

        assertThat(result.errors()).extracting(error -> error.type()).containsExactly(AssessmentUploadErrorType.EXISTING_COMPLAINT);
        // The complaint and the original assessment must be left untouched (nothing is stored).
        assertThat(complaintRepo.findByResultId(manualResult.getId())).isPresent();
        assertManualAssessment(participation1.getId(), 40.0, "first");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldNotCreateSubmissionForSubmissionlessParticipantWhenAnotherParticipantIsBlockedByComplaint() {
        // Regression test for the all-or-nothing contract: participation1 has a complained manual result that blocks the whole upload, while participation2 has no submission at
        // all. The upload writes nothing before the complaint gate has passed, so participation2 must not receive a submission — otherwise a rejected upload would leave that
        // submission behind even though it reports that nothing was stored.
        assessmentUploadService.importAssessments(programmingExercise, buildZip("Identifier,Manual points\n%s,40\n".formatted(identifier1), Map.of(identifier1 + ".txt", "first")));
        final Result manualResult = getManualResults(participation1.getId()).getFirst();
        complaintRepo.save(new Complaint().result(manualResult).complaintType(ComplaintType.COMPLAINT));
        assertThat(getSubmissions(participation2.getId())).isEmpty();

        final String csv = "Identifier,Manual points\n%s,90\n%s,70\n".formatted(identifier1, identifier2);
        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise,
                buildZip(csv, Map.of(identifier1 + ".txt", "second", identifier2 + ".txt", "feedback two")));

        assertThat(result.errors()).extracting(error -> error.type()).containsExactly(AssessmentUploadErrorType.EXISTING_COMPLAINT);
        // Nothing was stored: participation1 keeps its original assessment and participation2 never received a submission.
        assertManualAssessment(participation1.getId(), 40.0, "first");
        assertThat(getSubmissions(participation2.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldNotBlockUploadWhenOnlyASupersededSubmissionHasAComplaint() {
        // An older, superseded submission carries a complained manual result, while the latest submission carries a different manual result. The upload replaces only the latest
        // submission's result, so the complaint on the superseded result must neither block the upload nor be deleted.
        final Result supersededManualResult = participationUtilService.createSubmissionAndResult(participation1, 30, true);
        supersededManualResult.setAssessmentType(AssessmentType.MANUAL);
        resultRepository.saveAndFlush(supersededManualResult);
        complaintRepo.save(new Complaint().result(supersededManualResult).complaintType(ComplaintType.COMPLAINT));
        // A newer submission (higher id) becomes the latest; its manual result is the one the upload will actually replace.
        final Result latestManualResult = participationUtilService.createSubmissionAndResult(participation1, 50, true);
        latestManualResult.setAssessmentType(AssessmentType.MANUAL);
        resultRepository.saveAndFlush(latestManualResult);

        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,80\n".formatted(identifier1), Map.of(identifier1 + ".txt", "latest feedback")));

        assertThat(result.errors()).isEmpty();
        // The superseded result and its complaint are left untouched.
        assertThat(resultRepository.findById(supersededManualResult.getId())).isPresent();
        assertThat(complaintRepo.findByResultId(supersededManualResult.getId())).isPresent();
        // Only the latest submission's manual result was overwritten, in place and with the uploaded score.
        assertThat(getLatestSubmissionResults(participation1.getId()).stream().filter(Result::isManual).toList()).singleElement().satisfies(manualResult -> {
            assertThat(manualResult.getId()).isEqualTo(latestManualResult.getId());
            assertThat(manualResult.getScore()).isCloseTo(80.0, within(0.01));
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectPointsAboveMaximumIncludingBonusPoints() {
        programmingExercise.setBonusPoints(10.0);
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        final Map<String, String> textFiles = Map.of(identifier1 + ".txt", "feedback");

        final AssessmentUploadResultDTO accepted = assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,110\n".formatted(identifier1), textFiles));
        final AssessmentUploadResultDTO rejected = assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,110.01\n".formatted(identifier1), textFiles));

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
                buildZip("Identifier,Manual points\n%s,80\n".formatted(identifier1), textFiles));

        assertThat(result.errors()).extracting(error -> error.type()).containsExactly(AssessmentUploadErrorType.DUPLICATE_TEXT_FILE);
        assertThat(getManualResults(participation1.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectTextFileReusedBySuffixIdentifier() {
        // The login cross-check resolves against the owner of the participation, so participation1's student has to carry the login that is a suffix of the other identifier.
        // Restore it right after the import: the login is how addUsers finds and reuses the account between tests, and an account left renamed makes the next setup insert a
        // second one with the same email, which the unique index on that column rejects.
        final var student1 = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        student1.setLogin(identifier2);
        userTestRepository.saveAndFlush(student1);
        final String identifierWithSuffixLogin = participation1.getId() + "-" + identifier2;
        final String csv = "Identifier,Manual points\n%s,80\n%s,60\n".formatted(identifierWithSuffixLogin, identifier2);

        final AssessmentUploadResultDTO result;
        try {
            result = assessmentUploadService.importAssessments(programmingExercise, buildZip(csv, Map.of(identifierWithSuffixLogin + ".txt", "feedback")));
        }
        finally {
            student1.setLogin(TEST_PREFIX + "student1");
            userTestRepository.saveAndFlush(student1);
        }

        assertThat(result.errors()).extracting(error -> error.type()).containsExactly(AssessmentUploadErrorType.AMBIGUOUS_TEXT_FILE);
        assertThat(getManualResults(participation1.getId())).isEmpty();
        assertThat(getManualResults(participation2.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReportMalformedCsvSeparatelyFromEmptyCsv() {
        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n\"unterminated,80\n", Map.of(identifier1 + ".txt", "feedback")));

        assertThat(result.errors()).extracting(error -> error.type()).containsExactly(AssessmentUploadErrorType.MALFORMED_CSV);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectZipEntryAboveUncompressedSizeLimit() {
        final String oversizedFeedback = "x".repeat(10 * 1024 * 1024 + 1);

        assertThatThrownBy(() -> assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,80\n".formatted(identifier1), Map.of(identifier1 + ".txt", oversizedFeedback)))).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("maximum uncompressed size");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCountDirectoryEntryContentAgainstUncompressedSizeLimit() {
        final String oversizedDirectoryContent = "x".repeat(10 * 1024 * 1024 + 1);

        assertThatThrownBy(() -> assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,80\n".formatted(identifier1), Map.of("oversized/", oversizedDirectoryContent)))).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("maximum uncompressed size");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectCsvWithMoreThanTheMaximumNumberOfRows() {
        final StringBuilder csv = new StringBuilder("Identifier,Manual points\n");
        // One row more than the accepted maximum of 10000 data rows: parsing must stop and reject before materializing all rows.
        for (int row = 0; row <= 10_000; row++) {
            csv.append("row").append(row).append(",80\n");
        }

        assertThatThrownBy(() -> assessmentUploadService.importAssessments(programmingExercise, buildZip(csv.toString(), Map.of()))).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("more than the maximum");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectWholeUploadWhenOverallPointsColumnIsMissing() {
        final String csv = "Identifier,Name\n%s,Student One\n".formatted(identifier1);
        final Map<String, String> textFiles = new LinkedHashMap<>();
        textFiles.put(identifier1 + ".txt", "feedback");

        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise, buildZip(csv, textFiles));

        assertThat(result.errors()).extracting(error -> error.type()).containsExactly(AssessmentUploadErrorType.MISSING_MANUAL_POINTS_COLUMN);
        assertThat(getManualResults(participation1.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectUploadWithUnknownParticipationAndStoreNothing() {
        final String csv = "Identifier,Manual points\n%s,80\n999999-nobody,50\n".formatted(identifier1);
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
        final String csv = "Identifier,Manual points\n%s,80\n".formatted(identifier1);
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
    void shouldKeepTheAutomaticFeedbackOfAnOverwrittenAssessmentAndCountItTowardsTheScore() {
        // An assessment opened in the editor carries the automatic feedback of the build it was made against, and a tutor's points count on top of it. An upload replaces the
        // manual part of that assessment the same way, so the typed rows and their counters have to survive the overwrite and keep contributing to the score.
        assessmentUploadService.importAssessments(programmingExercise, buildZip("Identifier,Manual points\n%s,40\n".formatted(identifier1), Map.of(identifier1 + ".txt", "first")));
        final Result seeded = getManualResults(participation1.getId()).getFirst();
        final var testCase = testCaseRepository.findByExerciseId(programmingExercise.getId()).iterator().next();
        participationUtilService.addTestCaseFeedbackToResult(seeded, testCase, true, null);
        seeded.setTestCaseCount(1);
        seeded.setPassedTestCaseCount(1);
        resultRepository.save(seeded);

        assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,10\n".formatted(identifier1), Map.of(identifier1 + ".txt", "second")));

        final Result overwritten = getManualResults(participation1.getId()).getFirst();
        assertThat(overwritten.getId()).isEqualTo(seeded.getId());
        assertThat(testCaseFeedbackRepository.findWithTestCaseByResultIds(List.of(seeded.getId()))).hasSize(1);
        assertThat(overwritten.getTestCaseCount()).isEqualTo(1);
        assertThat(overwritten.getPassedTestCaseCount()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCountUploadedPointsOnTopOfTheAutomaticTestPoints() {
        // The uploaded number is manual points, not a final score: it is added to what the tests already awarded, exactly as a tutor's points are in the assessment editor.
        final double automaticPoints = seedAssessmentWithOnePassingTest();

        assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,10\n".formatted(identifier1), Map.of(identifier1 + ".txt", "ten on top")));
        final double scoreWithTen = getManualResults(participation1.getId()).getFirst().getScore();

        assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,25\n".formatted(identifier1), Map.of(identifier1 + ".txt", "twenty-five on top")));
        final double scoreWithTwentyFive = getManualResults(participation1.getId()).getFirst().getScore();

        assertThat(automaticPoints).isPositive();
        assertThat(scoreWithTen).isCloseTo(automaticPoints + 10, within(0.01));
        assertThat(scoreWithTwentyFive).isCloseTo(automaticPoints + 25, within(0.01));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldDeductNegativeUploadedPointsFromTheAutomaticTestPoints() {
        // Deducting is what a manual assessment usually does once the tests have scored the submission, so a negative value is a valid row rather than an invalid one.
        final double automaticPoints = seedAssessmentWithOnePassingTest();

        assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,-5\n".formatted(identifier1), Map.of(identifier1 + ".txt", "five off for style")));

        final Result result = getManualResults(participation1.getId()).getFirst();
        assertThat(result.getScore()).isCloseTo(automaticPoints - 5, within(0.01));
        assertThat(result.getFeedbacks().iterator().next().getCredits()).isCloseTo(-5, within(0.01));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldStartANewAssessmentFromTheBuildAndNotFromANewerAthenaResult() {
        // A student can ask Athena for non-graded feedback after the build, which leaves a newer result on the same submission carrying suggestion credits and none of the
        // build's tests or penalties. Starting from that one would lose the test points and count the suggestions instead.
        final Submission submission = participationUtilService.addSubmission(participation1, new ProgrammingSubmission().submitted(true));
        final Result buildResult = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusHours(2), submission);
        final var testCase = testCaseRepository.findByExerciseId(programmingExercise.getId()).iterator().next();
        participationUtilService.addTestCaseFeedbackToResult(buildResult, testCase, true, null);
        participationUtilService.addFeedbackToResult(submissionPolicyDeduction(), buildResult);
        buildResult.setTestCaseCount(1);
        buildResult.setPassedTestCaseCount(1);
        resultRepository.save(buildResult);
        final Result athenaResult = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC_ATHENA, ZonedDateTime.now().minusMinutes(5), submission);
        participationUtilService.addFeedbackToResult(new Feedback().credits(99.0).detailText("an Athena suggestion").type(FeedbackType.AUTOMATIC), athenaResult);

        assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,5\n".formatted(identifier1), Map.of(identifier1 + ".txt", "five on top")));

        final Result created = getManualResults(participation1.getId()).getFirst();
        assertThat(testCaseFeedbackRepository.findWithTestCaseByResultIds(List.of(created.getId()))).hasSize(1);
        assertThat(created.getFeedbacks()).anyMatch(Feedback::isSubmissionPolicyFeedback);
        assertThat(created.getFeedbacks()).noneMatch(feedback -> "an Athena suggestion".equals(feedback.getDetailText()));
        assertThat(created.getPassedTestCaseCount()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCopyTheBuildFeedbackOfEveryParticipantInAConstantNumberOfQueries() {
        // The copy used to run two feedback selects per newly assessed participant, which the existing guard never saw because it stops at the complaint gate. This one
        // imports successfully, so it reaches the creation path where the build feedback is copied.
        giveEveryParticipantABuild();
        // Two disjoint groups, so both imports create assessments and copy build feedback, and neither has to undo the other's work first.
        final List<String> twoParticipants = identifiers.subList(0, 2);
        final List<String> fourParticipants = identifiers.subList(2, 6);

        queryInterceptor.startQueryCount();
        assessmentUploadService.importAssessments(programmingExercise, buildZip(buildCsvWithoutTextFiles(twoParticipants), buildFeedbackFiles(twoParticipants)));
        final long feedbackReadsForTwo = countTypedFeedbackReadsBeforeTheResultsAreWritten();

        queryInterceptor.startQueryCount();
        assessmentUploadService.importAssessments(programmingExercise, buildZip(buildCsvWithoutTextFiles(fourParticipants), buildFeedbackFiles(fourParticipants)));
        final long feedbackReadsForFour = countTypedFeedbackReadsBeforeTheResultsAreWritten();

        // Only the reads are counted: the inserts of the assessments and their feedback scale with the batch by nature, the two reads that collect the build feedback do not.
        assertThat(feedbackReadsForTwo).isPositive();
        assertThat(feedbackReadsForFour).isEqualTo(feedbackReadsForTwo);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldKeepASubmissionPolicyDeductionWhenOverwritingAnAssessment() {
        // A submission policy penalty is ordinary AUTOMATIC feedback sitting next to the manual rows, not typed test feedback. It is part of what the build decided, so
        // replacing the manual part of an assessment must leave it in place; dropping it would hand the participant back the points the policy took away.
        final double automaticPoints = seedAssessmentWithOnePassingTest();
        final Result seeded = getManualResults(participation1.getId()).getFirst();
        participationUtilService.addFeedbackToResult(submissionPolicyDeduction(), seeded);

        assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,5\n".formatted(identifier1), Map.of(identifier1 + ".txt", "five on top")));

        final Result overwritten = getManualResults(participation1.getId()).getFirst();
        assertThat(overwritten.getFeedbacks()).anyMatch(Feedback::isSubmissionPolicyFeedback);
        assertThat(overwritten.getScore()).isCloseTo(automaticPoints - 10 + 5, within(0.01));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCopyASubmissionPolicyDeductionOntoANewlyCreatedAssessment() {
        // The same deduction has to reach a participant assessed for the first time, which is what the assessment editor does when it opens an assessment on that build.
        final Submission submission = participationUtilService.addSubmission(participation1, new ProgrammingSubmission().submitted(true));
        final Result automaticResult = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusHours(1), submission);
        participationUtilService.addFeedbackToResult(submissionPolicyDeduction(), automaticResult);

        assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,5\n".formatted(identifier1), Map.of(identifier1 + ".txt", "five on top")));

        final Result created = getManualResults(participation1.getId()).getFirst();
        assertThat(created.getFeedbacks()).anyMatch(Feedback::isSubmissionPolicyFeedback);
        assertThat(created.getScore()).isCloseTo(0.0, within(0.01));
    }

    /**
     * How many times the typed automatic feedback tables were read while the assessments were still being built, which is what copying the build feedback costs. Counted up
     * to the first assessment written, so the reads the notification does afterwards — one pair per announced result, and not this path's doing — stay out of it.
     */
    private long countTypedFeedbackReadsBeforeTheResultsAreWritten() {
        final List<String> statements = queryInterceptor.getStatements().stream().map(statement -> statement.toLowerCase(Locale.ROOT)).toList();
        final int firstResultWrite = IntStream.range(0, statements.size()).filter(index -> statements.get(index).startsWith("insert into result")).findFirst()
                .orElse(statements.size());
        return statements.subList(0, firstResultWrite).stream()
                .filter(statement -> statement.startsWith("select") && (statement.contains("from test_case_feedback") || statement.contains("from sca_feedback"))).count();
    }

    /** Gives every participant of the exercise a build with one passing test, so an import has automatic feedback to copy for each of them. */
    private void giveEveryParticipantABuild() {
        final var testCase = testCaseRepository.findByExerciseId(programmingExercise.getId()).iterator().next();
        for (final String identifier : identifiers) {
            final long participationId = Long.parseLong(identifier.substring(0, identifier.indexOf('-')));
            final var participation = studentParticipationRepository.findWithEagerSubmissionsResultsFeedbacksById(participationId).orElseThrow();
            final Submission submission = participationUtilService.addSubmission(participation, new ProgrammingSubmission().submitted(true));
            final Result buildResult = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusHours(2), submission);
            participationUtilService.addTestCaseFeedbackToResult(buildResult, testCase, true, null);
        }
    }

    private Feedback submissionPolicyDeduction() {
        return new Feedback().credits(-10.0).text(Feedback.SUBMISSION_POLICY_FEEDBACK_IDENTIFIER + "Submission Penalty Policy").detailText("policy").positive(false)
                .type(FeedbackType.AUTOMATIC);
    }

    /**
     * Gives participation1 an assessment carrying one passing test case, the way an assessment opened in the editor carries the automatic feedback of its build, and returns
     * the points those tests are worth so a caller can assert what the uploaded points are added to.
     */
    private double seedAssessmentWithOnePassingTest() {
        assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,0\n".formatted(identifier1), Map.of(identifier1 + ".txt", "nothing manual yet")));
        final Result seeded = getManualResults(participation1.getId()).getFirst();
        final var testCase = testCaseRepository.findByExerciseId(programmingExercise.getId()).iterator().next();
        participationUtilService.addTestCaseFeedbackToResult(seeded, testCase, true, null);
        resultRepository.save(seeded);
        // Re-upload zero manual points so the stored score is exactly what the tests award.
        assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,0\n".formatted(identifier1), Map.of(identifier1 + ".txt", "nothing manual yet")));
        return getManualResults(participation1.getId()).getFirst().getScore();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectASecondRowSpellingTheSameParticipationIdDifferently() {
        // "042-login" parses to the same participation as "42-login" but differs as an identifier string, and each row brings a text file of its own, so neither the raw
        // identifier check nor text-file matching catches the pair. Without deduplication by resolved id the participant would be assessed twice by one upload.
        final String aliasIdentifier = "0" + identifier1;
        final String csv = "Identifier,Manual points\n%s,80\n%s,40\n".formatted(identifier1, aliasIdentifier);

        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(programmingExercise,
                buildZip(csv, Map.of(identifier1 + ".txt", "first", aliasIdentifier + ".txt", "second")));

        assertThat(result.errors()).extracting(error -> error.type()).containsExactly(AssessmentUploadErrorType.DUPLICATE_IDENTIFIER);
        assertThat(getManualResults(participation1.getId())).isEmpty();
        assertThat(getSubmissions(participation1.getId())).hasSizeLessThanOrEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectUploadWhenLoginDoesNotMatchParticipation() {
        final String csv = "Identifier,Manual points\n%s-wrong,80\n".formatted(identifier1);
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
    void shouldLoadExistingResultsInAConstantNumberOfQueriesRegardlessOfBatchSize() {
        // Seed a stored manual result for every participant, then attach a complaint to one of them. A re-import is then rejected at the complaint gate — after every target
        // submission's results have been loaded, but before anything is written. This isolates the read path (the previously per-submission lazy result load) from the storage
        // path,
        // whose per-row result and feedback inserts scale with the batch size regardless and would otherwise mask the read N+1.
        final AssessmentUploadResultDTO seed = assessmentUploadService.importAssessments(programmingExercise,
                buildZip(buildCsvWithoutTextFiles(identifiers), buildFeedbackFiles(identifiers)));
        assertThat(seed.errors()).isEmpty();
        complaintRepo.save(new Complaint().result(getManualResults(participation1.getId()).getFirst()).complaintType(ComplaintType.COMPLAINT));

        // Re-import three participants (one blocked by the complaint), then all six. Because each existing submission's results are fetched in one bulk query rather than lazily
        // per
        // submission, the read path runs the same number of queries regardless of the batch size — a regression guard against the per-participant N+1.
        final List<String> threeIdentifiers = identifiers.subList(0, 3);
        queryInterceptor.startQueryCount();
        final AssessmentUploadResultDTO threeResult = assessmentUploadService.importAssessments(programmingExercise,
                buildZip(buildCsvWithoutTextFiles(threeIdentifiers), buildFeedbackFiles(threeIdentifiers)));
        final long queriesForThree = queryInterceptor.getQueryCount();

        queryInterceptor.startQueryCount();
        final AssessmentUploadResultDTO sixResult = assessmentUploadService.importAssessments(programmingExercise,
                buildZip(buildCsvWithoutTextFiles(identifiers), buildFeedbackFiles(identifiers)));
        final long queriesForSix = queryInterceptor.getQueryCount();

        // Both re-imports are rejected by the complaint on participation1 (all-or-nothing), so neither writes anything and the query count reflects only the read path.
        assertThat(threeResult.errors()).extracting(error -> error.type()).containsOnly(AssessmentUploadErrorType.EXISTING_COMPLAINT);
        assertThat(sixResult.errors()).extracting(error -> error.type()).containsOnly(AssessmentUploadErrorType.EXISTING_COMPLAINT);
        assertThat(queriesForSix).isEqualTo(queriesForThree);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectNullArguments() {
        final MockMultipartFile zip = buildZip("Identifier,Manual points\n", new LinkedHashMap<>());
        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadService.importAssessments(null, zip));
        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadService.importAssessments(programmingExercise, null));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectExerciseWithoutPositiveMaximumPoints() {
        final MockMultipartFile zip = buildZip("Identifier,Manual points\n", new LinkedHashMap<>());
        programmingExercise.setMaxPoints(0.0);

        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadService.importAssessments(programmingExercise, zip))
                .withMessage("The exercise for a manual assessment upload must have positive maximum points");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldNotAccumulateResultsWhenUploadingRepeatedly() {
        final Result automaticResult = participationUtilService.createSubmissionAndResult(participation1, 25, true);
        automaticResult.setAssessmentType(AssessmentType.AUTOMATIC);
        resultRepository.saveAndFlush(automaticResult);

        assessmentUploadService.importAssessments(programmingExercise, buildZip("Identifier,Manual points\n%s,40\n".formatted(identifier1), Map.of(identifier1 + ".txt", "first")));
        assessmentUploadService.importAssessments(programmingExercise,
                buildZip("Identifier,Manual points\n%s,90\n".formatted(identifier1), Map.of(identifier1 + ".txt", "second")));

        // Repeated uploads overwrite the same manual assessment instead of appending a new one, so the submission still holds exactly the automatic result and one manual result.
        final Set<Result> submissionResults = getLatestSubmissionResults(participation1.getId());
        assertThat(submissionResults).doesNotContainNull().hasSize(2);
        assertThat(submissionResults).anyMatch(result -> result.getAssessmentType() == AssessmentType.AUTOMATIC);
        assertThat(submissionResults.stream().filter(Result::isManual).toList()).singleElement()
                .satisfies(manualResult -> assertThat(manualResult.getScore()).isCloseTo(90.0, within(0.01)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectUploadForExerciseWithMultipleCorrectionRounds() {
        // getNumberOfCorrectionRounds() is always 1 for course exercises, so a spy emulates a (multi-round) exam exercise without the exam setup.
        final ProgrammingExercise exerciseWithTwoRounds = Mockito.spy(programmingExercise);
        Mockito.doReturn(2).when(exerciseWithTwoRounds).getNumberOfCorrectionRounds();
        final MockMultipartFile zip = buildZip("Identifier,Manual points\n%s,80\n".formatted(identifier1), Map.of(identifier1 + ".txt", "feedback"));

        assertThatThrownBy(() -> assessmentUploadService.importAssessments(exerciseWithTwoRounds, zip)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("correction round");
        assertThat(getManualResults(participation1.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectUploadForAnAutomaticExerciseThatOnlyAllowsComplaints() {
        // Enabling complaints on an automatically assessed exercise lets tutors answer complaints manually, and keeps the assessment dashboard reachable. It does not turn the
        // exercise into a manually assessed one, so a batch of uploaded assessments must still be refused.
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExercise.setAllowComplaintsForAutomaticAssessments(true);
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        final MockMultipartFile zip = buildZip("Identifier,Manual points\n%s,80\n".formatted(identifier1), Map.of(identifier1 + ".txt", "feedback"));

        assertThatThrownBy(() -> assessmentUploadService.importAssessments(programmingExercise, zip)).isInstanceOf(BadRequestAlertException.class);
        assertThat(getManualResults(participation1.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectUploadForAnExerciseAssessedAutomatically() {
        // The dashboard disables the upload for such an exercise, which is a hint rather than a guard: the endpoint has to refuse it on its own.
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExercise.setAllowComplaintsForAutomaticAssessments(false);
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        final MockMultipartFile zip = buildZip("Identifier,Manual points\n%s,80\n".formatted(identifier1), Map.of(identifier1 + ".txt", "feedback"));

        assertThatThrownBy(() -> assessmentUploadService.importAssessments(programmingExercise, zip)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("manual assessment enabled");
        assertThat(getManualResults(participation1.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectTheUploadRequestItselfWhenManualResultsAreNotAllowed() throws Exception {
        // Re-enabling the disabled button in the browser gets an instructor as far as the request, and no further: the gate lives behind the endpoint, not in front of it.
        userUtilService.enrollPrefixedUsersInCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember(), TEST_PREFIX);
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExercise.setAllowComplaintsForAutomaticAssessments(false);
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        final MockMultipartFile zip = buildZip("Identifier,Manual points\n%s,80\n".formatted(identifier1), Map.of(identifier1 + ".txt", "feedback"));

        request.postWithMultipartFile("/api/assessment/exercises/" + programmingExercise.getId() + "/manual-assessments", null, "ignored", zip, AssessmentUploadResultDTO.class,
                HttpStatus.BAD_REQUEST);

        assertThat(getManualResults(participation1.getId())).isEmpty();
        assertThat(getSubmissions(participation1.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectUploadWhenManualResultsAreNotAllowed() {
        // Move the due date into the future so manual results are not yet allowed for the (non-exam) exercise.
        programmingExercise.setDueDate(ZonedDateTime.now().plusDays(3));
        programmingExercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(5));
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        final MockMultipartFile zip = buildZip("Identifier,Manual points\n%s,80\n".formatted(identifier1), Map.of(identifier1 + ".txt", "feedback"));

        assertThatThrownBy(() -> assessmentUploadService.importAssessments(programmingExercise, zip)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("not allowed");
        assertThat(getManualResults(participation1.getId())).isEmpty();
    }

    private Set<Result> getLatestSubmissionResults(final long participationId) {
        final var participation = studentParticipationRepository.findWithEagerSubmissionsResultsFeedbacksById(participationId).orElseThrow();
        final Submission latestSubmission = participation.getSubmissions().stream().max(Comparator.comparing(Submission::getId)).orElseThrow();
        return latestSubmission.getResults();
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

    private List<Submission> getSubmissions(final long participationId) {
        final var participation = studentParticipationRepository.findWithEagerSubmissionsResultsFeedbacksById(participationId).orElseThrow();
        return participation.getSubmissions().stream().toList();
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

    /** Reads every entry of a zip archive into a name-to-content map (UTF-8), preserving the entry order. */
    private Map<String, String> readZipEntries(final byte[] zipBytes) throws IOException {
        final Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    /** Fills the empty "Manual points" field of every data row of a template CSV with the given value, leaving the header untouched. */
    private String fillTemplatePoints(final String templateCsv, final double points) {
        final String[] lines = templateCsv.split("\n", -1);
        final StringBuilder filled = new StringBuilder(lines[0]).append("\n");
        for (int lineIndex = 1; lineIndex < lines.length; lineIndex++) {
            if (!lines[lineIndex].isBlank()) {
                // Each data row ends with the empty "Manual points" field, so appending the value fills it in.
                filled.append(lines[lineIndex]).append(points).append("\n");
            }
        }
        return filled.toString();
    }

    private String buildCsvWithoutTextFiles(final List<String> participantIdentifiers) {
        return "Identifier,Manual points\n" + participantIdentifiers.stream().map(identifier -> identifier + ",80").collect(java.util.stream.Collectors.joining("\n")) + "\n";
    }

    /** One {@code <identifier>.txt} feedback file per identifier, so every CSV row of {@link #buildCsvWithoutTextFiles} has an exact-match text file and the upload succeeds. */
    private Map<String, String> buildFeedbackFiles(final List<String> participantIdentifiers) {
        final Map<String, String> feedbackFiles = new LinkedHashMap<>();
        participantIdentifiers.forEach(identifier -> feedbackFiles.put(identifier + ".txt", "feedback"));
        return feedbackFiles;
    }
}
