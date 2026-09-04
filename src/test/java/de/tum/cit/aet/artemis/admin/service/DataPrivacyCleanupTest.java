package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.UserActivityService;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.course.service.CourseDataRetentionService;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.util.ZipFileTestUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismSubmissionRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Integration tests for the destructive, irreversible data-privacy cleanup paths, run against a real database:
 * <ul>
 * <li>the two-phase old-course flow end to end: the real warn+archive phase
 * ({@link CourseDataRetentionService#warnAndArchiveDueCourses()}) produces a real archive that actually contains the
 * students' submissions, and the real reset phase ({@link CourseDataRetentionService#resetDueCourses()}) then deletes the
 * student data while keeping the course material and the archive backup, and</li>
 * <li>the not-enrolled-user permanent deletion ({@link DataCleanupService#deleteNotEnrolledUsers()}), which only removes
 * accounts after all blocking domain references have been cleaned.</li>
 * </ul>
 * These are the operations where a wrong gate would silently destroy data, so every test asserts both the intended
 * deletion <b>and</b> that everything outside the gate survives. The selection/gating logic in isolation is additionally
 * covered by the pure-unit {@code CourseDataRetentionServiceTest}; the query-level user filter by
 * {@code UserRepositoryTest#testFindAllNotEnrolledUsersModifiedBefore}.
 */
class DataPrivacyCleanupTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "dataprivacycleanup";

    @Autowired
    private CourseDataRetentionService courseDataRetentionService;

    @Autowired
    private UserActivityService userActivityService;

    @Autowired
    private DataCleanupService dataCleanupService;

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
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ZipFileTestUtilService zipFileTestUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private PlagiarismSubmissionRepository plagiarismSubmissionRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private PostTestRepository postRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Value("${artemis.course-archives-path}")
    private Path courseArchivesDirPath;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void resetDueCoursesResetsWarnedCoursePastGraceKeepsMaterialAndSparesOthers() {
        ZonedDateTime now = ZonedDateTime.now();

        // Due: warned 40 days ago (grace is 30 days), not yet reset -> its student data must be deleted.
        Course dueCourse = courseUtilService.addCourseWithModelingAndTextExercise();
        Exercise dueExercise = dueCourse.getExercises().iterator().next();
        StudentParticipation dueParticipation = participationUtilService.createAndSaveParticipationForExercise(dueExercise, TEST_PREFIX + "student1");
        attachConfig(dueCourse, now.minusDays(40), null);

        // Warned only 5 days ago -> still within the grace period, must be spared.
        Course withinGraceCourse = courseUtilService.addCourseWithModelingAndTextExercise();
        StudentParticipation withinGraceParticipation = participationUtilService.createAndSaveParticipationForExercise(withinGraceCourse.getExercises().iterator().next(),
                TEST_PREFIX + "student1");
        attachConfig(withinGraceCourse, now.minusDays(5), null);

        // Never warned (no configuration) -> must be spared even though old; a course is never reset without a warning.
        Course neverWarnedCourse = courseUtilService.addCourseWithModelingAndTextExercise();
        StudentParticipation neverWarnedParticipation = participationUtilService.createAndSaveParticipationForExercise(neverWarnedCourse.getExercises().iterator().next(),
                TEST_PREFIX + "student1");

        // Warned 40 days ago, but an instructor has since moved the end date into the future -> no longer past its
        // retention deadline, so it must be spared and its now-stale warning withdrawn.
        Course extendedCourse = courseUtilService.addCourseWithModelingAndTextExercise();
        StudentParticipation extendedParticipation = participationUtilService.createAndSaveParticipationForExercise(extendedCourse.getExercises().iterator().next(),
                TEST_PREFIX + "student1");
        attachConfig(extendedCourse, now.minusDays(40), null, now.plusYears(1));

        // Warned 40 days ago, but placed under a data-retention hold (e.g. an objection was raised) -> must be spared.
        Course heldCourse = courseUtilService.addCourseWithModelingAndTextExercise();
        StudentParticipation heldParticipation = participationUtilService.createAndSaveParticipationForExercise(heldCourse.getExercises().iterator().next(),
                TEST_PREFIX + "student1");
        attachConfig(heldCourse, now.minusDays(40), null);
        heldCourse.getCourseConfiguration().setDataRetentionHold(true);
        courseRepository.save(heldCourse);

        // The count preview and the actual selection must agree (both derive from findCoursesDueForReset).
        List<Course> dueForReset = courseDataRetentionService.findCoursesDueForReset();
        assertThat(dueForReset).extracting(Course::getId).contains(dueCourse.getId()).doesNotContain(withinGraceCourse.getId(), neverWarnedCourse.getId(), extendedCourse.getId(),
                heldCourse.getId());
        assertThat(dataCleanupService.countOldCoursesReset().courses()).isEqualTo(dueForReset.size());

        int reset = courseDataRetentionService.resetDueCourses();

        assertThat(reset).isGreaterThanOrEqualTo(1);
        // Due course: student participation deleted, but the course + its exercise + its configuration are preserved and
        // the reset is stamped so it is never reset again.
        assertThat(studentParticipationRepository.findById(dueParticipation.getId())).isEmpty();
        assertThat(courseRepository.findById(dueCourse.getId())).isPresent();
        assertThat(exerciseRepository.findById(dueExercise.getId())).isPresent();
        assertThat(courseConfigurationRepository.findByCourseId(dueCourse.getId())).get().extracting(CourseConfiguration::getStudentDataResetDate).isNotNull();

        // Spared courses: student data untouched and never stamped as reset.
        assertThat(studentParticipationRepository.findById(withinGraceParticipation.getId())).isPresent();
        assertThat(courseConfigurationRepository.findByCourseId(withinGraceCourse.getId())).get().extracting(CourseConfiguration::getStudentDataResetDate).isNull();
        assertThat(studentParticipationRepository.findById(neverWarnedParticipation.getId())).isPresent();

        // Courses that left the scope after being warned keep their student data, and their stale warning is withdrawn
        // so that becoming due again later requires a new warning and a full grace period.
        assertThat(studentParticipationRepository.findById(extendedParticipation.getId())).isPresent();
        assertThat(courseConfigurationRepository.findByCourseId(extendedCourse.getId())).get()
                .satisfies(configuration -> assertThat(configuration.getResetWarningSentDate()).isNull()).extracting(CourseConfiguration::getStudentDataResetDate).isNull();
        assertThat(studentParticipationRepository.findById(heldParticipation.getId())).isPresent();
        assertThat(courseConfigurationRepository.findByCourseId(heldCourse.getId())).get().satisfies(configuration -> assertThat(configuration.getResetWarningSentDate()).isNull())
                .extracting(CourseConfiguration::getStudentDataResetDate).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void findCoursesDueForWarningSelectsOnlyPastRetentionUnwarnedNonTestCourses() {
        ZonedDateTime now = ZonedDateTime.now();

        Course nonGradeDue = courseWithEnd("nongradedue", now.minusYears(2), false, false, null); // > 1y, non-grade -> due
        Course gradeDueNoConfig = courseWithEnd("gradedue", now.minusYears(6), null, false, null); // > 5y, no config (grade default) -> due
        Course gradeNotDue = courseWithEnd("gradenotdue", now.minusYears(2), true, false, null); // grade needs 5y -> not due
        Course recent = courseWithEnd("recent", now.minusMonths(1), null, false, null); // recent -> not due
        Course testCourseDue = courseWithEnd("testcourse", now.minusYears(6), null, true, null); // test course -> skip
        Course alreadyWarned = courseWithEnd("warned", now.minusYears(2), false, false, now.minusDays(3)); // already warned -> skip

        List<Long> dueIds = courseDataRetentionService.findCoursesDueForWarning().stream().map(Course::getId).toList();

        assertThat(dueIds).contains(nonGradeDue.getId(), gradeDueNoConfig.getId());
        assertThat(dueIds).doesNotContain(gradeNotDue.getId(), recent.getId(), testCourseDue.getId(), alreadyWarned.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void notEnrolledUserCleanupWarnsThenDeletesOnlyAfterGraceAndSparesTheRest() {
        Instant longAgo = ZonedDateTime.now().minusYears(1).toInstant();
        doReturn(true).when(mailSendingService).isMailConfigured();
        // The warning is sent synchronously and the "warned" stamp is only written when the send succeeds.
        doReturn(true).when(mailSendingService).buildAndSendSyncReporting(any(), any(), anyList(), any(), anyMap());

        int baselineWarnCount = dataCleanupService.countNotEnrolledUsersWarning().users();

        // Candidate: not enrolled (no groups), no admin authority, inactive, not deleted.
        User toDelete = notEnrolledUser(TEST_PREFIX + "todelete", longAgo);
        long toDeleteId = toDelete.getId();
        String originalLogin = toDelete.getLogin();

        // Must survive — each violates one guard:
        User enrolled = enrolledUser(TEST_PREFIX + "enrolled", longAgo); // enrolled -> keep
        User recent = notEnrolledUser(TEST_PREFIX + "recent", ZonedDateTime.now().toInstant()); // recently active -> keep

        // The Iris bot is excluded from both query phases; set it up (already warned past grace) only if the deployment
        // did not already seed it, so this test also verifies that it remains untouched.
        User irisBot = userUtilService.userExistsWithLogin(User.IRIS_BOT_LOGIN) ? null : notEnrolledUser(User.IRIS_BOT_LOGIN, longAgo);
        if (irisBot != null) {
            userActivityService.recordDeletionWarning(User.IRIS_BOT_LOGIN, ZonedDateTime.now().minusDays(31).toInstant());
        }

        // Phase 1 (warn): exactly the one candidate is counted (enrolled, recent, and the bot excluded).
        assertThat(dataCleanupService.countNotEnrolledUsersWarning().users()).isEqualTo(baselineWarnCount + 1);
        dataCleanupService.warnNotEnrolledUsers();
        verify(mailSendingService, atLeastOnce()).buildAndSendSyncReporting(any(), any(), anyList(), any(), anyMap());
        assertThat(userActivityService.findDeletionWarningSentDate(toDeleteId)).isNotNull();
        assertThat(userActivityService.findDeletionWarningSentDate(enrolled.getId())).isNull();
        assertThat(userActivityService.findDeletionWarningSentDate(recent.getId())).isNull();

        // Immediately after warning the account is within grace -> the delete phase deletes nobody yet.
        dataCleanupService.deleteNotEnrolledUsers();
        assertThat(userRepository.findById(toDeleteId)).get().extracting(User::isDeleted).isEqualTo(false);

        // Backdate the warning past the 30-day grace; now the account is due for deletion.
        userActivityService.recordDeletionWarning(originalLogin, ZonedDateTime.now().minusDays(31).toInstant());
        dataCleanupService.deleteNotEnrolledUsers();

        // The warned, past-grace account has no blocking references and is physically deleted.
        assertThat(userRepository.findById(toDeleteId)).isEmpty();

        // Enrolled and recently-active users are untouched; the Iris bot is never deleted even when warned past grace.
        assertThat(userRepository.findById(enrolled.getId())).get().extracting(User::isDeleted).isEqualTo(false);
        assertThat(userRepository.findById(recent.getId())).get().extracting(User::isDeleted).isEqualTo(false);
        if (irisBot != null) {
            User irisBotAfter = userRepository.findById(irisBot.getId()).orElseThrow();
            assertThat(irisBotAfter.isDeleted()).isFalse();
            assertThat(irisBotAfter.getLogin()).isEqualTo(User.IRIS_BOT_LOGIN);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void warnDoesNotStampOrDeleteWhenTheWarningCannotBeDelivered() {
        // The central GDPR guarantee: an account is only advanced to "warned" (and thus eligible for deletion) once a
        // warning was actually delivered. Here mail is configured but every send fails, so the account must not be
        // stamped and must never be deleted, even past the grace period.
        Instant longAgo = ZonedDateTime.now().minusYears(1).toInstant();
        doReturn(true).when(mailSendingService).isMailConfigured();
        doReturn(false).when(mailSendingService).buildAndSendSyncReporting(any(), any(), anyList(), any(), anyMap());
        User sendFails = notEnrolledUser(TEST_PREFIX + "sendfails", longAgo);

        dataCleanupService.warnNotEnrolledUsers();

        verify(mailSendingService, atLeastOnce()).buildAndSendSyncReporting(any(), any(), anyList(), any(), anyMap());
        assertThat(userActivityService.findDeletionWarningSentDate(sendFails.getId())).isNull();

        // No warning was ever stamped, so phase 2 cannot delete the account (even though it is well past the grace period).
        dataCleanupService.deleteNotEnrolledUsers();
        assertThat(userRepository.findById(sendFails.getId())).get().extracting(User::isDeleted).isEqualTo(false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void warnDoesNothingWhenMailIsNotConfigured() {
        // If mail is not configured the warn phase must warn nobody and stamp nobody, so no account can ever be scheduled
        // for deletion without prior notice.
        Instant longAgo = ZonedDateTime.now().minusYears(1).toInstant();
        doReturn(false).when(mailSendingService).isMailConfigured();
        User candidate = notEnrolledUser(TEST_PREFIX + "nomailcand", longAgo);

        dataCleanupService.warnNotEnrolledUsers();

        verify(mailSendingService, never()).buildAndSendSyncReporting(any(), any(), anyList(), any(), anyMap());
        assertThat(userActivityService.findDeletionWarningSentDate(candidate.getId())).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void warnCreatesRecoverableArchiveWithStudentDataThatSurvivesTheReset() throws IOException {
        // The core "no data lost" guarantee for enabling automation: before any student data is deleted, the warn phase
        // must produce a real, non-empty course archive that actually CONTAINS the students' submissions, and that
        // backup must survive the subsequent reset. This runs the REAL archive (CourseArchiveService) and the REAL
        // reset (CourseResetService) end to end; only the external mail service is stubbed.
        doReturn(true).when(mailSendingService).isMailConfigured();
        doReturn(true).when(mailSendingService).buildAndSendSyncReporting(any(), any(), anyList(), any(), anyMap());

        // A finished course with a real, submitted text submission for <prefix>student1.
        Course course = courseUtilService.addCourseWithModelingAndTextExercise();
        course.setStartDate(ZonedDateTime.now().minusYears(2).minusMonths(2));
        course.setEndDate(ZonedDateTime.now().minusYears(2)); // past the 1-year non-grade retention -> due for warning
        CourseConfiguration configuration = new CourseConfiguration();
        configuration.setCourse(course);
        configuration.setGradeRelevant(false);
        course.setCourseConfiguration(configuration);
        course = courseRepository.save(course);
        long courseId = course.getId();
        // so getInstructors() finds an eligible instructor: enrollment is a course role, not a group name
        userUtilService.enrollUserInCourse(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), course, CourseRole.INSTRUCTOR);
        TextExercise textExercise = course.getExercises().stream().filter(TextExercise.class::isInstance).map(TextExercise.class::cast).findFirst().orElseThrow();
        long textExerciseId = textExercise.getId();
        // Known submission content, so we can prove the archive actually contains the student's work.
        textExerciseUtilService.saveTextSubmission(textExercise, ParticipationFactory.generateTextSubmission("example text", Language.ENGLISH, true), TEST_PREFIX + "student1");
        assertThat(studentParticipationRepository.findByExerciseIdAndTestRunWithEagerSubmissionsResultAssessor(textExerciseId, false)).isNotEmpty();

        // Phase 1: warn + archive (real).
        int warned = courseDataRetentionService.warnAndArchiveDueCourses();
        assertThat(warned).isGreaterThanOrEqualTo(1);
        verify(mailSendingService, atLeastOnce()).buildAndSendSyncReporting(any(), any(), anyList(), any(), anyMap());

        Course archivedCourse = courseRepository.findById(courseId).orElseThrow();
        assertThat(archivedCourse.hasCourseArchive()).isTrue();
        Path archiveFile = courseArchivesDirPath.resolve(archivedCourse.getCourseArchivePath());
        assertThat(archiveFile).exists();
        assertThat(Files.size(archiveFile)).isPositive();
        // The archive really contains the student's submission content (not an empty or broken backup).
        Path extracted = zipFileTestUtilService.extractZipFileRecursively(archiveFile.toString());
        try (var files = Files.walk(extracted)) {
            assertThat(files.filter(Files::isRegularFile)).anyMatch(file -> fileContains(file, "example text"));
        }
        FileUtils.deleteDirectory(extracted.toFile());
        // The warning is stamped and the student data is still present (the reset has not happened yet).
        assertThat(courseConfigurationRepository.findByCourseId(courseId)).get().extracting(CourseConfiguration::getResetWarningSentDate).isNotNull();
        assertThat(studentParticipationRepository.findByExerciseIdAndTestRunWithEagerSubmissionsResultAssessor(textExerciseId, false)).isNotEmpty();

        // Backdate the warning past the 30-day grace period so the reset becomes due.
        CourseConfiguration stored = courseConfigurationRepository.findByCourseId(courseId).orElseThrow();
        stored.setResetWarningSentDate(ZonedDateTime.now().minusDays(31));
        courseConfigurationRepository.save(stored);

        // Phase 2: reset (real).
        int reset = courseDataRetentionService.resetDueCourses();
        assertThat(reset).isGreaterThanOrEqualTo(1);

        // Student data is gone, but the course, its exercise, and the archive backup all survive.
        assertThat(studentParticipationRepository.findByExerciseIdAndTestRunWithEagerSubmissionsResultAssessor(textExerciseId, false)).isEmpty();
        assertThat(courseRepository.findById(courseId)).isPresent();
        assertThat(exerciseRepository.findById(textExerciseId)).isPresent();
        assertThat(courseRepository.findById(courseId)).get().extracting(Course::hasCourseArchive).isEqualTo(true);
        assertThat(archiveFile).exists(); // the backup created before the reset is still on disk
        assertThat(courseConfigurationRepository.findByCourseId(courseId)).get().extracting(CourseConfiguration::getStudentDataResetDate).isNotNull();

        Files.deleteIfExists(archiveFile);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void deletePlagiarismCasesRemovesOnlyCasesOfCoursesEndedBeyondRetentionAndSparesRecent() {
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        // Old course: ended > 5 years ago (the grade-relevant retention cutoff) -> its plagiarism case must be deleted.
        Course oldCourse = courseUtilService.addCourseWithModelingAndTextExercise();
        Exercise oldExercise = oldCourse.getExercises().iterator().next();
        oldCourse.setEndDate(ZonedDateTime.now().minusYears(6));
        courseRepository.save(oldCourse);
        long oldCourseId = oldCourse.getId();
        exerciseUtilService.createPlagiarismCaseForUserForExercise(oldExercise, student, TEST_PREFIX, PlagiarismVerdict.PLAGIARISM);

        // Recent course: ended 1 year ago -> within the 5-year retention -> its plagiarism case must be kept.
        Course recentCourse = courseUtilService.addCourseWithModelingAndTextExercise();
        Exercise recentExercise = recentCourse.getExercises().iterator().next();
        recentCourse.setEndDate(ZonedDateTime.now().minusYears(1));
        courseRepository.save(recentCourse);
        long recentCourseId = recentCourse.getId();
        exerciseUtilService.createPlagiarismCaseForUserForExercise(recentExercise, student, TEST_PREFIX, PlagiarismVerdict.PLAGIARISM);

        // Capture the old-course case + its notification post to prove both are deleted (the post FK is RESTRICT, so a
        // wrong deletion order would throw instead of silently orphaning the post).
        List<PlagiarismCase> oldCasesBefore = plagiarismCaseRepository.findByCourseId(oldCourseId);
        assertThat(oldCasesBefore).hasSize(1);
        long oldCaseId = oldCasesBefore.getFirst().getId();
        long oldPostId = plagiarismCaseRepository.findByStudentIdAndExerciseIdWithPost(student.getId(), oldExercise.getId()).orElseThrow().getPost().getId();
        assertThat(postRepository.findById(oldPostId)).isPresent();

        // Attach a plagiarism submission whose plagiarism_case_id FK is RESTRICT. The delete must null this FK (via the
        // per-submission modifying query) BEFORE removing the case; without that null-out the case delete would throw an
        // FK violation. This makes the test fail if that null-out loop is ever removed.
        PlagiarismSubmission oldSubmission = new PlagiarismSubmission();
        oldSubmission.setStudentLogin(student.getLogin());
        oldSubmission.setSubmissionId(123L);
        oldSubmission.setPlagiarismCase(oldCasesBefore.getFirst());
        long oldSubmissionId = plagiarismSubmissionRepository.save(oldSubmission).getId();

        // The count preview counts the seeded old-course case (it is course-end-date driven, like the deletion itself).
        assertThat(dataCleanupService.countPlagiarismCasesOfOldCourses().plagiarismCases()).isGreaterThanOrEqualTo(1);

        dataCleanupService.deletePlagiarismCasesOfOldCourses();

        // Old course's plagiarism case AND its notification post are gone, but the course itself is untouched.
        assertThat(plagiarismCaseRepository.findById(oldCaseId)).isEmpty();
        assertThat(plagiarismCaseRepository.findByCourseId(oldCourseId)).isEmpty();
        assertThat(postRepository.findById(oldPostId)).isEmpty();
        assertThat(courseRepository.findById(oldCourseId)).isPresent();
        // The submission row survives, but its RESTRICT reference to the deleted case was cleared first.
        assertThat(plagiarismSubmissionRepository.findById(oldSubmissionId)).get().extracting(PlagiarismSubmission::getPlagiarismCase).isNull();

        // Recent course's plagiarism case survives untouched.
        assertThat(plagiarismCaseRepository.findByCourseId(recentCourseId)).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void deletePlagiarismCasesAlsoRemovesExamExerciseCasesOfOldCourses() {
        // Exam plagiarism cases reach their course via the exam, not directly, and are just as grade-relevant. They must
        // be included in the 5-year retention deletion (they are never erased by the course reset, which retains
        // plagiarism cases), so a regression narrowing the query back to only direct-course exercises is caught here.
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        ModelingExercise examExercise = examUtilService.addCourseExamExerciseGroupWithOneModelingExercise();
        Course examCourse = examExercise.getExerciseGroup().getExam().getCourse();
        examCourse.setEndDate(ZonedDateTime.now().minusYears(6));
        courseRepository.save(examCourse);
        exerciseUtilService.createPlagiarismCaseForUserForExercise(examExercise, student, TEST_PREFIX, PlagiarismVerdict.PLAGIARISM);

        long examCaseId = plagiarismCaseRepository.findByStudentIdAndExerciseIdWithPost(student.getId(), examExercise.getId()).orElseThrow().getId();
        assertThat(dataCleanupService.countPlagiarismCasesOfOldCourses().plagiarismCases()).isGreaterThanOrEqualTo(1);

        dataCleanupService.deletePlagiarismCasesOfOldCourses();

        assertThat(plagiarismCaseRepository.findById(examCaseId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void successfulLoginRecordsLastLoginDateUsedByTheInactivityGuard() {
        // The not-enrolled-user cleanup measures inactivity by lastLoginDate, which is recorded from the auth audit hook
        // on every successful login. If that wiring breaks, the guard silently falls back to the creation date and could
        // delete an actively-used account. This drives the real audit path (add -> isLoginSuccess -> recordLastLogin).
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Instant longAgo = ZonedDateTime.now().minusYears(1).toInstant();
        userActivityService.recordLogin(user.getLogin(), longAgo);

        Instant loginTime = ZonedDateTime.now().toInstant();
        auditEventRepository.add(new AuditEvent(loginTime, user.getLogin(), AuditEventConstants.AUTHENTICATION_SUCCESS, Map.of()));

        // The successful login moved lastLoginDate forward from the backdated value to ~now (compared with a margin
        // because the column stores millisecond precision), proving the audit hook records the activity signal.
        Instant recorded = userActivityService.findLastLoginDate(user.getId());
        assertThat(recorded).isAfter(longAgo).isBetween(loginTime.minusSeconds(60), loginTime.plusSeconds(60));
    }

    private static boolean fileContains(Path file, String text) {
        try {
            return Files.readString(file).contains(text);
        }
        catch (IOException e) {
            return false; // binary files (e.g. the uploaded PNG) are not readable as UTF-8 text; skip them
        }
    }

    private Course courseWithEnd(String suffix, ZonedDateTime endDate, Boolean gradeRelevant, boolean testCourse, ZonedDateTime warnedDate) {
        Course course = courseUtilService.createCourse();
        course.setTitle(TEST_PREFIX + suffix);
        course.setStartDate(endDate.minusMonths(3));
        course.setEndDate(endDate);
        course.setTestCourse(testCourse);
        if (gradeRelevant != null || warnedDate != null) {
            CourseConfiguration configuration = new CourseConfiguration();
            configuration.setCourse(course);
            configuration.setGradeRelevant(gradeRelevant == null || gradeRelevant);
            configuration.setResetWarningSentDate(warnedDate);
            course.setCourseConfiguration(configuration);
        }
        return courseRepository.save(course);
    }

    /**
     * Marks the course non-grade-relevant (1-year retention) and stamps the given lifecycle dates. The end date is moved
     * two years back as well: eligibility is re-evaluated at reset time, so a warned course only stays due while it is
     * also past its retention deadline, and the fixture courses end far in the future.
     */
    private void attachConfig(Course course, ZonedDateTime warnedDate, ZonedDateTime resetDate) {
        attachConfig(course, warnedDate, resetDate, ZonedDateTime.now().minusYears(2));
    }

    private void attachConfig(Course course, ZonedDateTime warnedDate, ZonedDateTime resetDate, ZonedDateTime endDate) {
        CourseConfiguration configuration = new CourseConfiguration();
        configuration.setCourse(course);
        configuration.setGradeRelevant(false);
        configuration.setResetWarningSentDate(warnedDate);
        configuration.setStudentDataResetDate(resetDate);
        course.setCourseConfiguration(configuration);
        course.setEndDate(endDate);
        courseRepository.save(course);
    }

    private User notEnrolledUser(String login, Instant lastLoginDate) {
        User user = userUtilService.createAndSaveUser(login);
        userActivityService.recordLogin(user.getLogin(), lastLoginDate);
        return user;
    }

    private User enrolledUser(String login, Instant lastLoginDate) {
        User user = userUtilService.createAndSaveUser(login);
        // enrollment is a course role now, so a plain group name would no longer make the user count as enrolled
        userUtilService.enrollUserInCourse(user, courseUtilService.createCourse(), CourseRole.STUDENT);
        userActivityService.recordLogin(user.getLogin(), lastLoginDate);
        return user;
    }
}
