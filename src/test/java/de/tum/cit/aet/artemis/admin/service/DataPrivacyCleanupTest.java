package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.course.service.CourseDataRetentionService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.fileupload.util.ZipFileTestUtilService;
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
 * <li>the not-enrolled-user soft-delete ({@link DataCleanupService#deleteNotEnrolledUsers()}), which anonymizes user
 * accounts.</li>
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

        // The count preview and the actual selection must agree (both derive from findCoursesDueForReset).
        List<Course> dueForReset = courseDataRetentionService.findCoursesDueForReset();
        assertThat(dueForReset).extracting(Course::getId).contains(dueCourse.getId()).doesNotContain(withinGraceCourse.getId(), neverWarnedCourse.getId());
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
    void deleteNotEnrolledUsersSoftDeletesOnlyInactiveNotEnrolledAccountsAndSparesTheRest() {
        Instant longAgo = ZonedDateTime.now().minusYears(1).toInstant();

        int baselineCount = dataCleanupService.countNotEnrolledUsers().users();

        // Selected for soft-delete: not enrolled (no groups), no admin authority, inactive, not deleted.
        User toDelete = notEnrolledUser(TEST_PREFIX + "todelete", longAgo);
        long toDeleteId = toDelete.getId();
        String originalLogin = toDelete.getLogin();

        // Must survive — each violates one guard:
        User enrolled = enrolledUser(TEST_PREFIX + "enrolled", longAgo); // enrolled -> keep
        User recent = notEnrolledUser(TEST_PREFIX + "recent", ZonedDateTime.now().toInstant()); // recently active -> keep

        // The Iris bot matches the query (no groups, inactive) but is explicitly excluded by the service; set it up only
        // if the deployment did not already seed it, to avoid mutating the real bot.
        User irisBot = userUtilService.userExistsWithLogin(User.IRIS_BOT_LOGIN) ? null : notEnrolledUser(User.IRIS_BOT_LOGIN, longAgo);

        // The count preview must add exactly the one deletable account (the Iris bot is excluded from the count too).
        assertThat(dataCleanupService.countNotEnrolledUsers().users()).isEqualTo(baselineCount + 1);

        dataCleanupService.deleteNotEnrolledUsers();

        // The deletable account is soft-deleted and anonymized (login/email replaced, deactivated).
        User deleted = userRepository.findById(toDeleteId).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getActivated()).isFalse();
        assertThat(deleted.getLogin()).isNotEqualTo(originalLogin);

        // Enrolled and recently-active users are untouched.
        assertThat(userRepository.findById(enrolled.getId())).get().extracting(User::isDeleted).isEqualTo(false);
        assertThat(userRepository.findById(recent.getId())).get().extracting(User::isDeleted).isEqualTo(false);

        // The Iris bot is never soft-deleted, even though it matches the selection query.
        if (irisBot != null) {
            User irisBotAfter = userRepository.findById(irisBot.getId()).orElseThrow();
            assertThat(irisBotAfter.isDeleted()).isFalse();
            assertThat(irisBotAfter.getLogin()).isEqualTo(User.IRIS_BOT_LOGIN);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void warnCreatesRecoverableArchiveWithStudentDataThatSurvivesTheReset() throws IOException {
        // The core "no data lost" guarantee for enabling automation: before any student data is deleted, the warn phase
        // must produce a real, non-empty course archive that actually CONTAINS the students' submissions, and that
        // backup must survive the subsequent reset. This runs the REAL archive (CourseArchiveService) and the REAL
        // reset (CourseResetService) end to end; only the external mail service is stubbed.
        doReturn(true).when(mailSendingService).isMailConfigured();
        doNothing().when(mailSendingService).buildAndSendAsync(any(), any(), anyList(), any(), anyMap());

        // A finished course with a real, submitted text submission for <prefix>student1.
        Course course = courseUtilService.addCourseWithModelingAndTextExercise();
        course.setInstructorGroupName(TEST_PREFIX + "instructor"); // so getInstructors() finds an eligible instructor
        course.setStartDate(ZonedDateTime.now().minusYears(2).minusMonths(2));
        course.setEndDate(ZonedDateTime.now().minusYears(2)); // past the 1-year non-grade retention -> due for warning
        CourseConfiguration configuration = new CourseConfiguration();
        configuration.setCourse(course);
        configuration.setGradeRelevant(false);
        course.setCourseConfiguration(configuration);
        course = courseRepository.save(course);
        long courseId = course.getId();
        TextExercise textExercise = course.getExercises().stream().filter(TextExercise.class::isInstance).map(TextExercise.class::cast).findFirst().orElseThrow();
        long textExerciseId = textExercise.getId();
        // Known submission content, so we can prove the archive actually contains the student's work.
        textExerciseUtilService.saveTextSubmission(textExercise, ParticipationFactory.generateTextSubmission("example text", Language.ENGLISH, true), TEST_PREFIX + "student1");
        assertThat(studentParticipationRepository.findByExerciseIdAndTestRunWithEagerSubmissionsResultAssessor(textExerciseId, false)).isNotEmpty();

        // Phase 1: warn + archive (real).
        int warned = courseDataRetentionService.warnAndArchiveDueCourses();
        assertThat(warned).isGreaterThanOrEqualTo(1);

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

    private void attachConfig(Course course, ZonedDateTime warnedDate, ZonedDateTime resetDate) {
        CourseConfiguration configuration = new CourseConfiguration();
        configuration.setCourse(course);
        configuration.setGradeRelevant(false);
        configuration.setResetWarningSentDate(warnedDate);
        configuration.setStudentDataResetDate(resetDate);
        course.setCourseConfiguration(configuration);
        courseRepository.save(course);
    }

    private User notEnrolledUser(String login, Instant lastLoginDate) {
        User user = userUtilService.createAndSaveUser(login);
        userRepository.updateLastLoginDate(user.getLogin(), lastLoginDate);
        return user;
    }

    private User enrolledUser(String login, Instant lastLoginDate) {
        User user = userUtilService.createAndSaveUser(login);
        user.setGroups(Set.of(TEST_PREFIX + "-enrolled-group"));
        user = userRepository.save(user);
        userRepository.updateLastLoginDate(user.getLogin(), lastLoginDate);
        return user;
    }
}
