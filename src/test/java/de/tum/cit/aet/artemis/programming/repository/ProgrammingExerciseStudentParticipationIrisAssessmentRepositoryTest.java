package de.tum.cit.aet.artemis.programming.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview;
import de.tum.cit.aet.artemis.iris.repository.IrisAssessmentRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Integration tests for the ask-user/Iris-assessment-review query methods added to
 * {@link ProgrammingExerciseStudentParticipationRepository} and {@link ProgrammingSubmissionRepository}. These methods contain
 * hand-written correlated-subquery JPQL ("latest submission with a positive score, ignoring later zero-score submissions"), which is
 * worth locking down against a real database.
 */
class ProgrammingExerciseStudentParticipationIrisAssessmentRepositoryTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "irisassessmentparticipationrepo";

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    @Autowired
    private ProgrammingExerciseStudentParticipationTestRepository participationRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private IrisAssessmentRepository irisAssessmentRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
        var course = courseUtilService.addEmptyCourse();
        exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, false);
    }

    private ProgrammingExerciseStudentParticipation participationFor(String login) {
        return participationUtilService.addStudentParticipationForProgrammingExercise(exercise, login);
    }

    private ProgrammingSubmission submissionWithScore(ProgrammingExerciseStudentParticipation participation, ZonedDateTime submissionDate, double score) {
        var submission = new ProgrammingSubmission();
        submission.setParticipation(participation);
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmissionDate(submissionDate);
        submission = programmingSubmissionRepository.save(submission);

        participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, submissionDate, submission, score > 0, true, score);

        return submission;
    }

    // =========================================================================
    // ProgrammingSubmissionRepository: findLatestSubmission...BeforeExerciseDueDateAndResultScoreGreaterThanZero...
    // =========================================================================

    @Test
    void findLatestSubmissionIdBeforeDueDate_returnsOnlyMatchingSubmissionWhenNoDueDateIsSet() {
        var participation = participationFor(TEST_PREFIX + "student1");
        var submission = submissionWithScore(participation, ZonedDateTime.now(ZONE).minusDays(1), 80.0);

        var result = programmingSubmissionRepository.findLatestSubmissionIdBeforeExerciseDueDateAndResultScoreGreaterThanZeroByParticipationId(participation.getId());

        assertThat(result).contains(submission.getId());
    }

    @Test
    void findLatestSubmissionIdBeforeDueDate_ignoresLaterZeroScoreSubmission() {
        var participation = participationFor(TEST_PREFIX + "student1");
        var scored = submissionWithScore(participation, ZonedDateTime.now(ZONE).minusDays(2), 80.0);
        submissionWithScore(participation, ZonedDateTime.now(ZONE).minusDays(1), 0.0);

        var result = programmingSubmissionRepository.findLatestSubmissionIdBeforeExerciseDueDateAndResultScoreGreaterThanZeroByParticipationId(participation.getId());

        assertThat(result).contains(scored.getId());
    }

    @Test
    void findLatestSubmissionIdBeforeDueDate_prefersLaterScoredSubmissionOverEarlierOne() {
        var participation = participationFor(TEST_PREFIX + "student1");
        submissionWithScore(participation, ZonedDateTime.now(ZONE).minusDays(2), 40.0);
        var latest = submissionWithScore(participation, ZonedDateTime.now(ZONE).minusDays(1), 90.0);

        var result = programmingSubmissionRepository.findLatestSubmissionIdBeforeExerciseDueDateAndResultScoreGreaterThanZeroByParticipationId(participation.getId());

        assertThat(result).contains(latest.getId());
    }

    @Test
    void findLatestSubmissionIdBeforeDueDate_excludesSubmissionsAfterDueDate() {
        var dueDate = ZonedDateTime.now(ZONE).minusDays(1);
        exercise.setDueDate(dueDate);
        exercise = programmingExerciseRepository.save(exercise);

        var participation = participationFor(TEST_PREFIX + "student1");
        var beforeDueDate = submissionWithScore(participation, dueDate.minusHours(1), 60.0);
        submissionWithScore(participation, dueDate.plusHours(1), 95.0);

        var result = programmingSubmissionRepository.findLatestSubmissionIdBeforeExerciseDueDateAndResultScoreGreaterThanZeroByParticipationId(participation.getId());

        assertThat(result).contains(beforeDueDate.getId());
    }

    @Test
    void findLatestSubmissionIdBeforeDueDate_includesSubmissionExactlyAtDueDate() {
        var dueDate = ZonedDateTime.now(ZONE).minusDays(1);
        exercise.setDueDate(dueDate);
        exercise = programmingExerciseRepository.save(exercise);

        var participation = participationFor(TEST_PREFIX + "student1");
        var atDueDate = submissionWithScore(participation, dueDate, 70.0);

        var result = programmingSubmissionRepository.findLatestSubmissionIdBeforeExerciseDueDateAndResultScoreGreaterThanZeroByParticipationId(participation.getId());

        assertThat(result).contains(atDueDate.getId());
    }

    @Test
    void findLatestSubmissionIdBeforeDueDate_returnsEmptyWhenNoScoredSubmissionExists() {
        var participation = participationFor(TEST_PREFIX + "student1");
        submissionWithScore(participation, ZonedDateTime.now(ZONE).minusDays(1), 0.0);

        var result = programmingSubmissionRepository.findLatestSubmissionIdBeforeExerciseDueDateAndResultScoreGreaterThanZeroByParticipationId(participation.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findLatestSubmissionWithEagerResultsAndFeedbacksAndBuildLogs_returnsSubmissionWithResultsLoaded() {
        var participation = participationFor(TEST_PREFIX + "student1");
        var submission = submissionWithScore(participation, ZonedDateTime.now(ZONE).minusDays(1), 80.0);

        var result = programmingSubmissionRepository
                .findLatestSubmissionWithEagerResultsAndFeedbacksAndBuildLogsBeforeExerciseDueDateAndResultScoreGreaterThanZeroByParticipationId(participation.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(submission.getId());
        assertThat(result.get().getResults()).isNotEmpty();
    }

    // =========================================================================
    // ProgrammingExerciseStudentParticipationRepository: iris-assessment lookups
    // =========================================================================

    @Test
    void findWithIrisAssessmentById_returnsParticipationWithAssessmentLoaded() {
        var participation = participationFor(TEST_PREFIX + "student1");
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var assessment = irisAssessmentRepository.save(new IrisAssessment(student, exercise));
        participation.setIrisAssessment(assessment);
        participation = participationRepository.save(participation);

        var loaded = participationRepository.findWithIrisAssessmentById(participation.getId());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getIrisAssessment()).isNotNull();
        assertThat(loaded.get().getIrisAssessment().getId()).isEqualTo(assessment.getId());
    }

    @Test
    void findWithIrisAssessmentByExerciseIdAndStudentLoginAndTestRun_regularUsesRegularAssessment() {
        var participation = participationFor(TEST_PREFIX + "student1");
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var regular = irisAssessmentRepository.save(new IrisAssessment(student, exercise));
        var inClass = irisAssessmentRepository.save(new IrisAssessment(student, exercise));
        participation.setIrisAssessment(regular);
        participation.setIrisAssessmentInClass(inClass);
        participationRepository.save(participation);

        var loaded = participationRepository.findWithIrisAssessmentByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), TEST_PREFIX + "student1", false, false);

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getIrisAssessment().getId()).isEqualTo(regular.getId());
    }

    @Test
    void findWithIrisAssessmentByExerciseIdAndStudentLoginAndTestRun_inClassUsesInClassAssessment() {
        var participation = participationFor(TEST_PREFIX + "student1");
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var inClass = irisAssessmentRepository.save(new IrisAssessment(student, exercise));
        participation.setIrisAssessmentInClass(inClass);
        participationRepository.save(participation);

        var loaded = participationRepository.findWithIrisAssessmentByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), TEST_PREFIX + "student1", true, false);

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getIrisAssessmentInClass().getId()).isEqualTo(inClass.getId());
    }

    @Test
    void findIrisAssessmentInClassIdsByExerciseId_andUnset_removeOnlyInClassAssessmentReferences() {
        var participation = participationFor(TEST_PREFIX + "student1");
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var regular = irisAssessmentRepository.save(new IrisAssessment(student, exercise));
        var inClass = irisAssessmentRepository.save(new IrisAssessment(student, exercise));
        participation.setIrisAssessment(regular);
        participation.setIrisAssessmentInClass(inClass);
        participationRepository.save(participation);

        var ids = participationRepository.findIrisAssessmentInClassIdsByExerciseId(exercise.getId());
        assertThat(ids).containsExactly(inClass.getId());

        participationRepository.unsetIrisAssessmentInClassByExerciseId(exercise.getId());

        var reloaded = participationRepository.findById(participation.getId()).orElseThrow();
        assertThat(reloaded.getIrisAssessmentInClass()).isNull();
        assertThat(reloaded.getIrisAssessment()).isNotNull();
    }

    @Test
    void findAllNonPracticeIrisAssessmentParticipationProjections_excludesPracticeAndZeroScoreParticipations() {
        var graded = participationFor(TEST_PREFIX + "student1");
        submissionWithScore(graded, ZonedDateTime.now(ZONE).minusDays(1), 80.0);

        var zeroScore = participationFor(TEST_PREFIX + "student2");
        submissionWithScore(zeroScore, ZonedDateTime.now(ZONE).minusDays(1), 0.0);

        var practiceStudent = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var practiceParticipation = new ProgrammingExerciseStudentParticipation();
        practiceParticipation.setProgrammingExercise(exercise);
        practiceParticipation.setParticipant(practiceStudent);
        practiceParticipation.setTestRun(true);
        practiceParticipation = participationRepository.save(practiceParticipation);
        submissionWithScore(practiceParticipation, ZonedDateTime.now(ZONE).minusDays(1), 80.0);

        var projections = participationRepository.findAllNonPracticeIrisAssessmentParticipationProjectionsByExerciseIdAndLatestResultScoreGreaterThanZero(exercise.getId());

        assertThat(projections).extracting(p -> p.id()).containsExactly(graded.getId());
    }

    @Test
    void findAllIrisAssessmentParticipationProjectionsByIdIn_mapsAssessmentVerdictAndStudent() {
        var participation = participationFor(TEST_PREFIX + "student1");
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var assessment = irisAssessmentRepository.save(new IrisAssessment(student, exercise));
        assessment.setVerdict(IrisVerdict.SUSPICIOUS);
        assessment.setVerdictReview(IrisVerdictReview.REJECTED);
        assessment = irisAssessmentRepository.save(assessment);
        participation.setIrisAssessment(assessment);
        participation = participationRepository.save(participation);

        var projections = participationRepository.findAllIrisAssessmentParticipationProjectionsByIdIn(Set.of(participation.getId()));

        assertThat(projections).hasSize(1);
        var projection = projections.iterator().next();
        assertThat(projection.studentLogin()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(projection.irisAssessmentVerdict()).isEqualTo(IrisVerdict.SUSPICIOUS);
        assertThat(projection.irisAssessmentVerdictReview()).isEqualTo(IrisVerdictReview.REJECTED);
    }

    @Test
    void findIrisAssessmentReviewParticipationIds_filtersBySuspiciousVerdict() {
        var suspicious = participationFor(TEST_PREFIX + "student1");
        submissionWithScore(suspicious, ZonedDateTime.now(ZONE).minusDays(1), 80.0);
        var suspiciousUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var suspiciousAssessment = new IrisAssessment(suspiciousUser, exercise);
        suspiciousAssessment.setVerdict(IrisVerdict.SUSPICIOUS);
        suspiciousAssessment = irisAssessmentRepository.save(suspiciousAssessment);
        suspicious.setIrisAssessment(suspiciousAssessment);
        participationRepository.save(suspicious);

        var missing = participationFor(TEST_PREFIX + "student2");
        submissionWithScore(missing, ZonedDateTime.now(ZONE).minusDays(1), 60.0);

        long courseId = exercise.getCourseViaExerciseGroupOrCourseMember().getId();

        var suspiciousOnly = participationRepository.findIrisAssessmentReviewParticipationIds(courseId, null, false, true, false, false, false, true, false, PageRequest.of(0, 20));
        assertThat(suspiciousOnly.getContent()).containsExactly(suspicious.getId());

        var missingOnly = participationRepository.findIrisAssessmentReviewParticipationIds(courseId, null, false, true, false, false, false, false, true, PageRequest.of(0, 20));
        assertThat(missingOnly.getContent()).containsExactly(missing.getId());

        var all = participationRepository.findIrisAssessmentReviewParticipationIds(courseId, null, false, false, false, false, false, false, false, PageRequest.of(0, 20));
        assertThat(all.getContent()).containsExactlyInAnyOrder(suspicious.getId(), missing.getId());
    }

    @Test
    void findIrisAssessmentReviewParticipationIds_filtersBySearchTerm() {
        var participation = participationFor(TEST_PREFIX + "student1");
        submissionWithScore(participation, ZonedDateTime.now(ZONE).minusDays(1), 80.0);
        var other = participationFor(TEST_PREFIX + "student2");
        submissionWithScore(other, ZonedDateTime.now(ZONE).minusDays(1), 70.0);

        long courseId = exercise.getCourseViaExerciseGroupOrCourseMember().getId();

        var result = participationRepository.findIrisAssessmentReviewParticipationIds(courseId, "%" + (TEST_PREFIX + "student1").toLowerCase() + "%", false, false, false, false,
                false, false, false, PageRequest.of(0, 20));

        assertThat(result.getContent()).containsExactly(participation.getId());
    }

    @Test
    void findByIdsWithLatestSubmissionAndIrisAssessment_loadsLatestSubmissionAndAssessment() {
        var participation = participationFor(TEST_PREFIX + "student1");
        submissionWithScore(participation, ZonedDateTime.now(ZONE).minusDays(2), 40.0);
        var latestSubmission = submissionWithScore(participation, ZonedDateTime.now(ZONE).minusDays(1), 90.0);
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var assessment = irisAssessmentRepository.save(new IrisAssessment(student, exercise));
        participation.setIrisAssessment(assessment);
        participation = participationRepository.save(participation);

        var loaded = participationRepository.findByIdsWithLatestSubmissionAndIrisAssessment(List.of(participation.getId()));

        assertThat(loaded).hasSize(1);
        var loadedParticipation = loaded.getFirst();
        assertThat(loadedParticipation.getIrisAssessment()).isNotNull();
        assertThat(loadedParticipation.getIrisAssessment().getId()).isEqualTo(assessment.getId());
        assertThat(loadedParticipation.getSubmissions()).extracting(s -> s.getId()).containsExactly(latestSubmission.getId());
    }

}
