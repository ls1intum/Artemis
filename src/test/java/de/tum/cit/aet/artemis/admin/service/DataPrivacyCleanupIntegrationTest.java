package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.course.service.CourseDataRetentionService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Integration tests for the destructive, irreversible data-privacy cleanup paths, run against a real database:
 * <ul>
 * <li>the two-phase old-course student-data reset (phase 2, {@link CourseDataRetentionService#resetDueCourses()}), which
 * actually deletes student participations while keeping the course material, and</li>
 * <li>the not-enrolled-user soft-delete ({@link DataCleanupService#deleteNotEnrolledUsers()}), which anonymizes user
 * accounts.</li>
 * </ul>
 * These are the operations where a wrong gate would silently destroy data, so every test asserts both the intended
 * deletion <b>and</b> that everything outside the gate survives. The selection/gating logic in isolation is additionally
 * covered by the pure-unit {@code CourseDataRetentionServiceTest}; the query-level user filter by
 * {@code UserRepositoryTest#testFindAllNotEnrolledUsersModifiedBefore}.
 */
class DataPrivacyCleanupIntegrationTest extends AbstractSpringIntegrationIndependentTest {

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

    private User notEnrolledUser(String login, Instant lastModifiedDate) {
        User user = userUtilService.createAndSaveUser(login);
        userRepository.updateLastModifiedDate(user.getId(), lastModifiedDate);
        return user;
    }

    private User enrolledUser(String login, Instant lastModifiedDate) {
        User user = userUtilService.createAndSaveUser(login);
        user.setGroups(Set.of(TEST_PREFIX + "-enrolled-group"));
        user = userRepository.save(user);
        userRepository.updateLastModifiedDate(user.getId(), lastModifiedDate);
        return user;
    }
}
