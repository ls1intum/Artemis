package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.UserActivityService;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.admin.config.DataCleanupProperties;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionVersion;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionVersionRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Full, no-mock integration tests for the <b>scheduled</b> data-privacy cleanup entry point
 * ({@link AutomaticDataCleanupScheduleService}), run against a real database. Unlike the pure-unit
 * {@code AutomaticDataCleanupScheduleServiceTest} (which mocks {@link DataCleanupService} and only checks flag
 * dispatch), these tests wire the real {@link DataCleanupService} bean and exercise the exact path that runs in
 * production when an admin enables the crons: flag gate → {@code runAsSystem} security wrapper → real deletion → real
 * repositories → real database.
 * <p>
 * This is the safety net for enabling automatic deletion: for every operation it asserts that a <b>disabled</b> schedule
 * deletes <b>nothing</b> (the default, so turning the feature on can never surprise-delete), and that an <b>enabled</b>
 * schedule deletes exactly the intended rows while sparing everything else. The schedule service is constructed with
 * explicit {@link DataCleanupProperties} so the enabled/disabled flags are controlled per test; the cutoffs mirror the
 * production defaults (5y / 1y / 30d / 8w / 8w / 6mo).
 */
class ScheduledDataCleanupTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "autocleanupsched";

    @Autowired
    private DataCleanupService dataCleanupService;

    @Autowired
    private UserActivityService userActivityService;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private CourseConfigurationRepository courseConfigurationRepository;

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private SubmissionVersionRepository submissionVersionRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private ResultTestRepository resultRepository;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExamTestRepository examTestRepository;

    @Autowired
    private StudentExamTestRepository studentExamTestRepository;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void scheduledCleanupDeletesNothingWhenAllSchedulesDisabled() {
        // Seed one target for every destructive operation, then run ALL scheduled jobs with every kill switch off. This
        // is the core guarantee that the feature is safe by default: enabling it later can never delete data that a
        // still-disabled schedule would have left alone.
        StudentParticipation resetParticipation = warnedDueCourseParticipation();
        User inactiveNotEnrolled = backdatedNotEnrolledUser(TEST_PREFIX + "disabled");
        Result oldNonLatestFeedbackResult = oldCourseNonLatestRatedResultWithFeedback();
        SubmissionVersion oldSubmissionVersion = oldCourseSubmissionVersion();
        long oldPlagiarismCaseId = oldCoursePlagiarismCaseId();

        AutomaticDataCleanupScheduleService disabled = scheduleService(false, false, false, false, false, false, false);
        disabled.warnOldCoursesReset();
        disabled.resetOldCourses();
        disabled.deleteOldFeedback();
        disabled.deleteOldSubmissionVersions();
        disabled.warnNotEnrolledUsers();
        disabled.deleteNotEnrolledUsers();
        disabled.deletePlagiarismCases();

        // Nothing was touched: no data deleted and no user even warned.
        assertThat(studentParticipationRepository.findById(resetParticipation.getId())).isPresent();
        User notEnrolledAfter = userRepository.findById(inactiveNotEnrolled.getId()).orElseThrow();
        assertThat(notEnrolledAfter.isDeleted()).isFalse();
        assertThat(userActivityService.findDeletionWarningSentDate(notEnrolledAfter.getId())).isNull();
        assertThat(feedbackRepository.findByResult(oldNonLatestFeedbackResult)).isNotEmpty();
        assertThat(submissionVersionRepository.findById(oldSubmissionVersion.getId())).isPresent();
        assertThat(plagiarismCaseRepository.findById(oldPlagiarismCaseId)).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void scheduledResetOldCoursesDeletesStudentDataWhenEnabled() {
        Course course = courseUtilService.addCourseWithModelingAndTextExercise();
        Exercise exercise = course.getExercises().iterator().next();
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        CourseConfiguration configuration = new CourseConfiguration();
        configuration.setCourse(course);
        configuration.setGradeRelevant(false);
        configuration.setResetWarningSentDate(ZonedDateTime.now().minusDays(40)); // warned > grace (30d) ago -> due
        course.setCourseConfiguration(configuration);
        // Eligibility is re-checked at reset time, so the course also has to be past its retention deadline (1 year for
        // a non-grade-relevant course); the fixture course ends far in the future.
        course.setEndDate(ZonedDateTime.now().minusYears(2));
        courseRepository.save(course);

        scheduleService(false, true, false, false, false, false, false).resetOldCourses();

        // Student data deleted, but the course material (course + exercise) and configuration are preserved and the
        // reset is stamped so the course is never reset again.
        assertThat(studentParticipationRepository.findById(participation.getId())).isEmpty();
        assertThat(courseRepository.findById(course.getId())).isPresent();
        assertThat(exerciseRepository.findById(exercise.getId())).isPresent();
        assertThat(courseConfigurationRepository.findByCourseId(course.getId())).get().extracting(CourseConfiguration::getStudentDataResetDate).isNotNull();
    }

    /**
     * The scheduled job authenticates itself with a synthetic authentication that has no principal, so anything on the
     * reset path resolving the current user would throw. That failure surfaced only for courses containing an exam, and
     * only after other student data had already been deleted: the exam step was collected as a failed item, the reset
     * was reported incomplete, the course was never stamped, and every following run tried again.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void scheduledResetOldCoursesResetsACourseContainingAnExam() {
        Course course = courseUtilService.addCourseWithModelingAndTextExercise();
        Exercise exercise = course.getExercises().iterator().next();
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");

        Exam exam = examUtilService.addExam(course);
        StudentExam studentExam = examUtilService.addStudentExamWithUser(exam, TEST_PREFIX + "student1");

        CourseConfiguration configuration = new CourseConfiguration();
        configuration.setCourse(course);
        configuration.setGradeRelevant(false);
        configuration.setResetWarningSentDate(ZonedDateTime.now().minusDays(40)); // warned > grace (30d) ago -> due
        course.setCourseConfiguration(configuration);
        course.setEndDate(ZonedDateTime.now().minusYears(2));
        courseRepository.save(course);

        scheduleService(false, true, false, false, false, false, false).resetOldCourses();

        // The exam's student data is gone, the exam itself is kept, and the reset completed - an incomplete reset would
        // leave studentDataResetDate null and retry the course forever.
        assertThat(studentExamTestRepository.findById(studentExam.getId())).isEmpty();
        assertThat(examTestRepository.findById(exam.getId())).isPresent();
        assertThat(studentParticipationRepository.findById(participation.getId())).isEmpty();
        assertThat(courseConfigurationRepository.findByCourseId(course.getId())).get().extracting(CourseConfiguration::getStudentDataResetDate).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void scheduledWarnNotEnrolledUsersStampsWarningOnlyForInactiveWhenEnabled() {
        doReturn(true).when(mailSendingService).isMailConfigured();
        doReturn(true).when(mailSendingService).buildAndSendSyncReporting(any(), any(), anyList(), any(), anyMap());
        User inactive = backdatedNotEnrolledUser(TEST_PREFIX + "warncand"); // inactive, not yet warned -> warn
        User recent = notEnrolledUser(TEST_PREFIX + "warnrecent"); // recently active -> must NOT be warned

        scheduleService(false, false, false, false, true, false, false).warnNotEnrolledUsers();

        verify(mailSendingService, atLeastOnce()).buildAndSendSyncReporting(any(), any(), anyList(), any(), anyMap());
        assertThat(userActivityService.findDeletionWarningSentDate(inactive.getId())).isNotNull();
        assertThat(userActivityService.findDeletionWarningSentDate(recent.getId())).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void scheduledDeleteNotEnrolledUsersPermanentlyDeletesOnlyWarnedPastGraceWhenEnabled() {
        Instant longAgo = ZonedDateTime.now().minusYears(1).toInstant();
        // Warned > 30-day grace ago, no login since the warning -> deleted.
        User due = warnedNotEnrolledUser(TEST_PREFIX + "delcand", longAgo, ZonedDateTime.now().minusDays(31).toInstant());
        long dueId = due.getId();
        // Warned only 5 days ago (still within grace) -> survives.
        User withinGrace = warnedNotEnrolledUser(TEST_PREFIX + "delgrace", longAgo, ZonedDateTime.now().minusDays(5).toInstant());
        // Warned > grace ago, but logged in AFTER the warning -> came back, so survives and the warning is cleared.
        User returned = warnedNotEnrolledUser(TEST_PREFIX + "delreturn", ZonedDateTime.now().toInstant(), ZonedDateTime.now().minusDays(31).toInstant());

        scheduleService(false, false, false, false, false, true, false).deleteNotEnrolledUsers();

        assertThat(userRepository.findById(dueId)).isEmpty();
        assertThat(userRepository.findById(withinGrace.getId())).get().extracting(User::isDeleted).isEqualTo(false);
        User returnedAfter = userRepository.findById(returned.getId()).orElseThrow();
        assertThat(returnedAfter.isDeleted()).isFalse();
        assertThat(userActivityService.findDeletionWarningSentDate(returnedAfter.getId())).isNull(); // warning cleared because the user logged in after being warned
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void scheduledFeedbackCleanupDeletesNonLatestFeedbackWhenEnabled() {
        Submission submission = oldCourseSubmission();
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        Result nonLatest = participationUtilService.generateResult(submission, instructor); // rated
        Feedback nonLatestFeedback = feedbackRepository.save(new Feedback());
        participationUtilService.addFeedbackToResult(nonLatestFeedback, nonLatest);
        Result latest = participationUtilService.generateResult(submission, instructor); // rated, newer id -> latest
        Feedback latestFeedback = feedbackRepository.save(new Feedback());
        participationUtilService.addFeedbackToResult(latestFeedback, latest);

        scheduleService(false, false, true, false, false, false, false).deleteOldFeedback();

        assertThat(feedbackRepository.findByResult(nonLatest)).isEmpty();
        assertThat(feedbackRepository.findByResult(latest)).isNotEmpty();
        // Only the feedback is removed; the results themselves are always kept.
        assertThat(resultRepository.existsById(nonLatest.getId())).isTrue();
        assertThat(resultRepository.existsById(latest.getId())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void scheduledSubmissionVersionCleanupDeletesWhenEnabled() {
        SubmissionVersion version = oldCourseSubmissionVersion();

        scheduleService(false, false, false, true, false, false, false).deleteOldSubmissionVersions();

        assertThat(submissionVersionRepository.findById(version.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void scheduledPlagiarismCaseCleanupDeletesWhenEnabled() {
        long plagiarismCaseId = oldCoursePlagiarismCaseId();

        scheduleService(false, false, false, false, false, false, true).deletePlagiarismCases();

        assertThat(plagiarismCaseRepository.findById(plagiarismCaseId)).isEmpty();
    }

    private AutomaticDataCleanupScheduleService scheduleService(boolean warn, boolean reset, boolean feedback, boolean submissionVersions, boolean notEnrolledWarn,
            boolean notEnrolled, boolean plagiarismCases) {
        return new AutomaticDataCleanupScheduleService(dataCleanupService,
                new DataCleanupProperties(5, 1, 30, 8, 8, 6, 30, warn, reset, feedback, submissionVersions, notEnrolledWarn, notEnrolled, plagiarismCases));
    }

    /**
     * Creates an old course with a student participation and stamps a reset warning 40 days ago (grace is 30 days), so
     * the course is due for a student-data reset.
     */
    private StudentParticipation warnedDueCourseParticipation() {
        Course course = courseUtilService.addCourseWithModelingAndTextExercise();
        Exercise exercise = course.getExercises().iterator().next();
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        CourseConfiguration configuration = new CourseConfiguration();
        configuration.setCourse(course);
        configuration.setGradeRelevant(false);
        configuration.setResetWarningSentDate(ZonedDateTime.now().minusDays(40));
        course.setCourseConfiguration(configuration);
        courseRepository.save(course);
        return participation;
    }

    /** Creates a submission belonging to a course that ended two years ago (well before the 8-week cutoff). */
    private Submission oldCourseSubmission() {
        Course course = courseUtilService.addCourseWithModelingAndTextExercise();
        course.setStartDate(ZonedDateTime.now().minusYears(2).minusMonths(3));
        course.setEndDate(ZonedDateTime.now().minusYears(2));
        course = courseRepository.save(course);
        Exercise exercise = course.getExercises().stream().filter(TextExercise.class::isInstance).findFirst().orElseThrow();
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        return participationUtilService.addSubmission(participation, ParticipationFactory.generateTextSubmission("content", Language.ENGLISH, true));
    }

    private SubmissionVersion oldCourseSubmissionVersion() {
        Submission submission = oldCourseSubmission();
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        return submissionVersionRepository.save(ParticipationFactory.generateSubmissionVersion("keystrokes", submission, student));
    }

    /** Creates a plagiarism case (with its notification post) for a course that ended six years ago (past the 5y cutoff). */
    private long oldCoursePlagiarismCaseId() {
        Course course = courseUtilService.addCourseWithModelingAndTextExercise();
        Exercise exercise = course.getExercises().iterator().next();
        course.setEndDate(ZonedDateTime.now().minusYears(6));
        courseRepository.save(course);
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        exerciseUtilService.createPlagiarismCaseForUserForExercise(exercise, student, TEST_PREFIX, PlagiarismVerdict.PLAGIARISM);
        return plagiarismCaseRepository.findByCourseId(course.getId()).stream().map(PlagiarismCase::getId).findFirst().orElseThrow();
    }

    private Result oldCourseNonLatestRatedResultWithFeedback() {
        Submission submission = oldCourseSubmission();
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        Result nonLatest = participationUtilService.generateResult(submission, instructor);
        Feedback feedback = feedbackRepository.save(new Feedback());
        participationUtilService.addFeedbackToResult(feedback, nonLatest);
        // A newer rated result makes the first one non-latest (so its feedback is a deletion candidate).
        participationUtilService.generateResult(submission, instructor);
        return nonLatest;
    }

    private User notEnrolledUser(String login) {
        return userUtilService.createAndSaveUser(login);
    }

    private User backdatedNotEnrolledUser(String login) {
        User user = userUtilService.createAndSaveUser(login);
        userActivityService.recordLogin(user.getLogin(), ZonedDateTime.now().minusYears(1).toInstant());
        return user;
    }

    private User warnedNotEnrolledUser(String login, Instant lastLogin, Instant warningDate) {
        User user = userUtilService.createAndSaveUser(login);
        userActivityService.recordLogin(user.getLogin(), lastLogin);
        userActivityService.recordDeletionWarning(user.getLogin(), warningDate);
        return user;
    }
}
