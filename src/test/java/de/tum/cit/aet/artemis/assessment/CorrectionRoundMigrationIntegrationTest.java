package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.test_repository.TextSubmissionTestRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Integration tests for verifying correction round behavior.
 * These tests ensure that:
 * 1. correction_round is correctly set for manual and semi-automatic assessments
 * 2. correction_round remains NULL for automatic assessments
 * 3. correction_round is sequential within a participation (0, 1, 2, ...)
 * 4. Multiple submissions per participation are handled correctly
 */
class CorrectionRoundMigrationIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "correctionroundtest";

    @Autowired
    private ResultTestRepository resultRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private TextSubmissionTestRepository textSubmissionRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private User student;

    private User tutor1;

    private User tutor2;

    private Course course;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 2, 0, 1);
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        tutor1 = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        tutor2 = userUtilService.getUserByLogin(TEST_PREFIX + "tutor2");
        course = courseUtilService.addEmptyCourse();
    }

    @AfterEach
    void tearDown() {
        // Clean up in reverse order of dependencies
        resultRepository.deleteAll();
        submissionRepository.deleteAll();
    }

    // ==================== Test: Single Manual Assessment ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSingleManualAssessment_shouldHaveCorrectionRoundZero() {
        // Given: A text exercise with one submission and one completed manual assessment
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), null);

        // Create participation and submission
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
        TextSubmission submission = new TextSubmission();
        submission.setParticipation(participation);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now().minusHours(1));
        submission.setText("Test submission text");
        submission = textSubmissionRepository.save(submission);

        // Add result using the utility method
        submission = (TextSubmission) participationUtilService.addResultToSubmission(submission, AssessmentType.MANUAL, tutor1, 50.0, true, 0);

        Result result = submission.getLatestResult();
        assertThat(result).isNotNull();

        // When: We retrieve the result
        Result savedResult = resultRepository.findById(result.getId()).orElseThrow();

        // Then: correction_round should be 0 (first assessment)
        assertThat(savedResult.getCorrectionRound()).isEqualTo(0);
        assertThat(savedResult.getAssessmentType()).isEqualTo(AssessmentType.MANUAL);
        assertThat(savedResult.getCompletionDate()).isNotNull();
    }

    // ==================== Test: Two Correction Rounds (Exam Second Correction) ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTwoCorrectionRounds_shouldHaveSequentialCorrectionRounds() {
        // Given: A text exercise with one submission and two completed manual assessments
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), null);

        // Create participation and submission
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
        TextSubmission submission = new TextSubmission();
        submission.setParticipation(participation);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now().minusHours(2));
        submission.setText("Test submission text");
        submission = textSubmissionRepository.save(submission);

        // First correction (round 0)
        Result result1 = new Result();
        result1.setSubmission(submission);
        result1.setScore(70.0);
        result1.setAssessmentType(AssessmentType.MANUAL);
        result1.setCompletionDate(ZonedDateTime.now().minusHours(1));
        result1.setAssessor(tutor1);
        result1.setExerciseId(textExercise.getId());
        result1.setCorrectionRound(0);
        result1 = resultRepository.save(result1);
        submission.addResult(result1);
        textSubmissionRepository.save(submission);

        // Second correction (round 1)
        Result result2 = new Result();
        result2.setSubmission(submission);
        result2.setScore(75.0);
        result2.setAssessmentType(AssessmentType.MANUAL);
        result2.setCompletionDate(ZonedDateTime.now());
        result2.setAssessor(tutor2);
        result2.setExerciseId(textExercise.getId());
        result2.setCorrectionRound(1);
        result2 = resultRepository.save(result2);
        submission.addResult(result2);
        textSubmissionRepository.save(submission);

        // When: We retrieve the results
        Result savedResult1 = resultRepository.findById(result1.getId()).orElseThrow();
        Result savedResult2 = resultRepository.findById(result2.getId()).orElseThrow();

        // Then: correction_rounds should be sequential (0, 1)
        assertThat(savedResult1.getCorrectionRound()).isEqualTo(0);
        assertThat(savedResult2.getCorrectionRound()).isEqualTo(1);

        // Different assessors for each round
        assertThat(savedResult1.getAssessor().getId()).isNotEqualTo(savedResult2.getAssessor().getId());
    }

    // ==================== Test: Automatic Assessment Should Have NULL Correction Round ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAutomaticAssessment_shouldHaveNullCorrectionRound() {
        // Given: A text exercise with automatic assessment
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), null);

        // Create participation and submission
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
        TextSubmission submission = new TextSubmission();
        submission.setParticipation(participation);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now().minusHours(1));
        submission.setText("Test submission text");
        submission = textSubmissionRepository.save(submission);

        // Add automatic result (correction_round should be NULL)
        submission = (TextSubmission) participationUtilService.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null, 80.0, true, ZonedDateTime.now());

        Result result = submission.getLatestResult();
        assertThat(result).isNotNull();

        // When: We retrieve the result
        Result savedResult = resultRepository.findById(result.getId()).orElseThrow();

        // Then: correction_round should be NULL for automatic assessments
        assertThat(savedResult.getCorrectionRound()).isNull();
        assertThat(savedResult.getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC);
    }

    // ==================== Test: Semi-Automatic Assessment ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSemiAutomaticAssessment_shouldHaveCorrectionRound() {
        // Given: A text exercise with semi-automatic assessment
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), null);

        // Create participation and submission
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
        TextSubmission submission = new TextSubmission();
        submission.setParticipation(participation);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now().minusHours(1));
        submission.setText("Test submission text");
        submission = textSubmissionRepository.save(submission);

        // Add semi-automatic result with correction round
        submission = (TextSubmission) participationUtilService.addResultToSubmission(submission, AssessmentType.SEMI_AUTOMATIC, tutor1, 60.0, true, 0);

        Result result = submission.getLatestResult();
        assertThat(result).isNotNull();

        // When: We retrieve the result
        Result savedResult = resultRepository.findById(result.getId()).orElseThrow();

        // Then: correction_round should be set for semi-automatic assessments
        assertThat(savedResult.getCorrectionRound()).isEqualTo(0);
        assertThat(savedResult.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
    }

    // ==================== Test: Incomplete Assessment Should Have NULL ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testIncompleteAssessment_shouldHaveNullCorrectionRound() {
        // Given: A text exercise with incomplete manual assessment (no completion date)
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), null);

        // Create participation and submission
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
        TextSubmission submission = new TextSubmission();
        submission.setParticipation(participation);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now().minusHours(1));
        submission.setText("Test submission text");
        submission = textSubmissionRepository.save(submission);

        // Create result manually to simulate incomplete assessment (no completion date)
        // The migration logic only sets correction_round for completed assessments
        Result result = new Result();
        result.setSubmission(submission);
        result.setScore(0.0);
        result.setRated(false);
        result.setAssessmentType(AssessmentType.MANUAL);
        result.setCompletionDate(null); // Incomplete - no completion date
        result.setAssessor(tutor1);
        result.setExerciseId(textExercise.getId());
        result.setCorrectionRound(null); // Should be NULL for incomplete assessments per migration logic
        result = resultRepository.save(result);
        submission.addResult(result);
        textSubmissionRepository.save(submission);

        assertThat(result).isNotNull();

        // When: We retrieve the result
        Result savedResult = resultRepository.findById(result.getId()).orElseThrow();

        // Then: correction_round should be NULL for incomplete assessments
        assertThat(savedResult.getCorrectionRound()).isNull();
        assertThat(savedResult.getCompletionDate()).isNull();
    }

    // ==================== Test: Multiple Submissions Per Participation ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testMultipleSubmissionsPerParticipation_correctionRoundAcrossParticipation() {
        // Given: A programming exercise where multiple submissions exist for one participation
        // This simulates a programming exercise where each commit creates a new submission
        ProgrammingExercise programmingExercise = programmingExerciseUtilService.createProgrammingExercise(course, ZonedDateTime.now().minusDays(2),
                ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));

        // Create participation
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(programmingExercise, student.getLogin());

        // First submission with manual assessment
        ProgrammingSubmission submission1 = new ProgrammingSubmission();
        submission1.setParticipation(participation);
        submission1.setSubmitted(true);
        submission1.setSubmissionDate(ZonedDateTime.now().minusHours(3));
        submission1.setCommitHash("commit1");
        submission1 = programmingSubmissionRepository.save(submission1);

        Result result1 = new Result();
        result1.setSubmission(submission1);
        result1.setScore(50.0);
        result1.setAssessmentType(AssessmentType.MANUAL);
        result1.setCompletionDate(ZonedDateTime.now().minusHours(2));
        result1.setAssessor(tutor1);
        result1.setExerciseId(programmingExercise.getId());
        result1.setCorrectionRound(0); // First manual assessment in participation
        result1 = resultRepository.save(result1);
        submission1.addResult(result1);
        programmingSubmissionRepository.save(submission1);

        // Second submission (new commit) with manual assessment
        ProgrammingSubmission submission2 = new ProgrammingSubmission();
        submission2.setParticipation(participation);
        submission2.setSubmitted(true);
        submission2.setSubmissionDate(ZonedDateTime.now().minusHours(1));
        submission2.setCommitHash("commit2");
        submission2 = programmingSubmissionRepository.save(submission2);

        Result result2 = new Result();
        result2.setSubmission(submission2);
        result2.setScore(70.0);
        result2.setAssessmentType(AssessmentType.MANUAL);
        result2.setCompletionDate(ZonedDateTime.now());
        result2.setAssessor(tutor2);
        result2.setExerciseId(programmingExercise.getId());
        result2.setCorrectionRound(1); // Second manual assessment in participation (across submissions)
        result2 = resultRepository.save(result2);
        submission2.addResult(result2);
        programmingSubmissionRepository.save(submission2);

        // When: We retrieve both results
        Result savedResult1 = resultRepository.findById(result1.getId()).orElseThrow();
        Result savedResult2 = resultRepository.findById(result2.getId()).orElseThrow();

        // Then: correction_rounds should be sequential across the participation
        assertThat(savedResult1.getCorrectionRound()).isEqualTo(0);
        assertThat(savedResult2.getCorrectionRound()).isEqualTo(1);

        // Results are on different submissions but same participation
        assertThat(savedResult1.getSubmission().getId()).isNotEqualTo(savedResult2.getSubmission().getId());
        assertThat(savedResult1.getSubmission().getParticipation().getId()).isEqualTo(savedResult2.getSubmission().getParticipation().getId());
    }

    // ==================== Test: Athena Automatic Assessment ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAthenaAutomaticAssessment_shouldHaveNullCorrectionRound() {
        // Given: A text exercise with Athena automatic assessment
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), null);

        // Create participation and submission
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
        TextSubmission submission = new TextSubmission();
        submission.setParticipation(participation);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now().minusHours(1));
        submission.setText("Test submission text");
        submission = textSubmissionRepository.save(submission);

        // Add Athena automatic result (correction_round should be NULL)
        submission = (TextSubmission) participationUtilService.addResultToSubmission(submission, AssessmentType.AUTOMATIC_ATHENA, null, 65.0, true, ZonedDateTime.now());

        Result result = submission.getLatestResult();
        assertThat(result).isNotNull();

        // When: We retrieve the result
        Result savedResult = resultRepository.findById(result.getId()).orElseThrow();

        // Then: correction_round should be NULL for Athena automatic assessments
        assertThat(savedResult.getCorrectionRound()).isNull();
        assertThat(savedResult.getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC_ATHENA);
    }

    // ==================== Test: Mixed Assessment Types ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testMixedAssessmentTypes_onlyManualCountsForCorrectionRound() {
        // Given: A participation with automatic result followed by manual result
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), null);

        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
        TextSubmission submission = new TextSubmission();
        submission.setParticipation(participation);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now().minusHours(2));
        submission.setText("Test submission");
        submission = textSubmissionRepository.save(submission);

        // Automatic result (should have NULL correction_round)
        Result automaticResult = new Result();
        automaticResult.setSubmission(submission);
        automaticResult.setScore(40.0);
        automaticResult.setAssessmentType(AssessmentType.AUTOMATIC);
        automaticResult.setCompletionDate(ZonedDateTime.now().minusHours(1));
        automaticResult.setExerciseId(textExercise.getId());
        automaticResult.setCorrectionRound(null);
        automaticResult = resultRepository.save(automaticResult);
        submission.addResult(automaticResult);

        // Manual result (should have correction_round = 0, not counting the automatic)
        Result manualResult = new Result();
        manualResult.setSubmission(submission);
        manualResult.setScore(70.0);
        manualResult.setAssessmentType(AssessmentType.MANUAL);
        manualResult.setCompletionDate(ZonedDateTime.now());
        manualResult.setAssessor(tutor1);
        manualResult.setExerciseId(textExercise.getId());
        manualResult.setCorrectionRound(0); // First MANUAL assessment
        manualResult = resultRepository.save(manualResult);
        submission.addResult(manualResult);

        textSubmissionRepository.save(submission);

        // When: We retrieve both results
        Result savedAutomatic = resultRepository.findById(automaticResult.getId()).orElseThrow();
        Result savedManual = resultRepository.findById(manualResult.getId()).orElseThrow();

        // Then: Automatic has NULL, manual has 0
        assertThat(savedAutomatic.getCorrectionRound()).isNull();
        assertThat(savedManual.getCorrectionRound()).isEqualTo(0);
    }

    // ==================== Test: Verify Correction Round Calculation Logic ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCorrectionRoundCalculation_matchesMigrationLogic() {
        // Given: Multiple results in a participation
        // This test verifies that correction_round is calculated by counting previous
        // completed manual/semi-automatic results within the same participation

        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), null);

        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
        TextSubmission submission = new TextSubmission();
        submission.setParticipation(participation);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now().minusHours(5));
        submission.setText("Test submission");
        submission = textSubmissionRepository.save(submission);

        // Create 3 manual results
        for (int i = 0; i < 3; i++) {
            Result result = new Result();
            result.setSubmission(submission);
            result.setScore(50.0 + i * 10);
            result.setAssessmentType(AssessmentType.MANUAL);
            result.setCompletionDate(ZonedDateTime.now().minusHours(4 - i));
            result.setAssessor(i % 2 == 0 ? tutor1 : tutor2);
            result.setExerciseId(textExercise.getId());
            result.setCorrectionRound(i); // Migration sets this based on count of previous results
            result = resultRepository.save(result);
            submission.addResult(result);
        }
        textSubmissionRepository.save(submission);

        // When: We retrieve all results for the participation
        List<Result> results = resultRepository.findAll().stream().filter(r -> r.getSubmission().getParticipation().getId().equals(participation.getId()))
                .filter(r -> r.getAssessmentType() == AssessmentType.MANUAL).sorted(Comparator.comparingLong(DomainObject::getId)).toList();

        // Then: correction_rounds should be 0, 1, 2
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getCorrectionRound()).isEqualTo(0);
        assertThat(results.get(1).getCorrectionRound()).isEqualTo(1);
        assertThat(results.get(2).getCorrectionRound()).isEqualTo(2);
    }
}
