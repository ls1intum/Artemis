package de.tum.cit.aet.artemis.account.repository;

import static de.tum.cit.aet.artemis.account.util.UserFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.UserActivityService;
import de.tum.cit.aet.artemis.account.service.user.PasswordService;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.account.util.UserFactory;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class UserRepositoryTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "userrepotest";

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private UserActivityService userActivityService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Test
    void testFindAllNotEnrolledUsers() {
        List<User> expected = userRepository.saveAllOrUpdate(userUtilService.generateActivatedUsers(TEST_PREFIX, passwordService.hashPassword(USER_PASSWORD), Set.of(), 1, 3));
        // Should not find administrators
        List<User> unexpected = userRepository
                .saveAllOrUpdate(userUtilService.generateActivatedUsers(TEST_PREFIX, passwordService.hashPassword(USER_PASSWORD), Set.of(Authority.ADMIN_AUTHORITY), 4, 4));
        // Should not find super administrators
        List<User> superAdmins = userRepository
                .saveAllOrUpdate(userUtilService.generateActivatedUsers(TEST_PREFIX, passwordService.hashPassword(USER_PASSWORD), Set.of(Authority.SUPER_ADMIN_AUTHORITY), 5, 5));
        unexpected.addAll(superAdmins);
        // Should not find deleted users
        List<User> deleted = userUtilService.generateActivatedUsers(TEST_PREFIX, passwordService.hashPassword(USER_PASSWORD), Set.of(), 6, 7);
        deleted.forEach(user -> user.setDeleted(true));
        unexpected.addAll(userRepository.saveAllOrUpdate(deleted));
        unexpected.add(irisBot(ZonedDateTime.now().minusYears(1).toInstant()));

        final List<String> actual = userRepository.findAllNotEnrolledUsers();

        assertThat(actual).doesNotContainAnyElementsOf(unexpected.stream().map(User::getLogin).toList());
        assertThat(actual).containsAll(expected.stream().map(User::getLogin).toList());
    }

    /**
     * Phase 1 selection: verifies which not-enrolled, inactive, not-yet-warned users are picked to be warned. Every
     * "must survive" user violates exactly one guard (recently active / enrolled / admin / super-admin / Iris bot /
     * already deleted / already warned), so a broken clause makes the corresponding assertion fail.
     */
    @Test
    void testFindNotEnrolledUsersToWarn() {
        final Instant cutoff = ZonedDateTime.now().minusMonths(6).toInstant();
        final Instant longAgo = ZonedDateTime.now().minusYears(1).toInstant();

        User toWarn = createUser(TEST_PREFIX + "warncand", false, Set.of(), false, longAgo); // -> warn
        User recent = createUser(TEST_PREFIX + "warnrecent", false, Set.of(), false, ZonedDateTime.now().toInstant()); // active -> keep
        User enrolled = createUser(TEST_PREFIX + "warnenrolled", true, Set.of(), false, longAgo); // enrolled -> keep
        User admin = createUser(TEST_PREFIX + "warnadmin", false, Set.of(Authority.ADMIN_AUTHORITY), false, longAgo); // admin -> keep
        User superAdmin = createUser(TEST_PREFIX + "warnsuper", false, Set.of(Authority.SUPER_ADMIN_AUTHORITY), false, longAgo); // super admin -> keep
        User irisBot = irisBot(longAgo); // protected bot -> keep
        User deleted = createUser(TEST_PREFIX + "warndeleted", false, Set.of(), true, longAgo); // already deleted -> keep
        User alreadyWarned = createUser(TEST_PREFIX + "warnalready", false, Set.of(), false, longAgo); // already warned -> keep
        userActivityService.recordDeletionWarning(alreadyWarned.getLogin(), longAgo);

        final List<String> logins = userRepository.findNotEnrolledUsersToWarn(cutoff).stream().map(User::getLogin).toList();

        assertThat(logins).contains(toWarn.getLogin());
        assertThat(logins).doesNotContain(recent.getLogin(), enrolled.getLogin(), admin.getLogin(), superAdmin.getLogin(), irisBot.getLogin(), deleted.getLogin(),
                alreadyWarned.getLogin());
    }

    /**
     * Phase 2 selection: verifies which warned users are picked for deletion. Only a warned, past-grace, still-inactive
     * account with no login since the warning is selected; within-grace, logged-in-since, and never-warned accounts are
     * spared.
     */
    @Test
    void testFindNotEnrolledUserLoginsToDelete() {
        final Instant graceCutoff = ZonedDateTime.now().minusDays(30).toInstant();
        final Instant longAgo = ZonedDateTime.now().minusYears(1).toInstant();
        final Instant warnedPastGrace = ZonedDateTime.now().minusDays(31).toInstant();
        final Instant warnedWithinGrace = ZonedDateTime.now().minusDays(5).toInstant();

        User due = createUser(TEST_PREFIX + "delcand", false, Set.of(), false, longAgo);
        userActivityService.recordDeletionWarning(due.getLogin(), warnedPastGrace); // warned past grace, no login since -> delete
        User withinGrace = createUser(TEST_PREFIX + "delgrace", false, Set.of(), false, longAgo);
        userActivityService.recordDeletionWarning(withinGrace.getLogin(), warnedWithinGrace); // still within grace -> keep
        User loggedInSince = createUser(TEST_PREFIX + "delloggedin", false, Set.of(), false, ZonedDateTime.now().toInstant());
        userActivityService.recordDeletionWarning(loggedInSince.getLogin(), warnedPastGrace); // logged in after warning -> keep
        User notWarned = createUser(TEST_PREFIX + "delnotwarned", false, Set.of(), false, longAgo); // never warned -> keep
        User irisBot = irisBot(longAgo);
        userActivityService.recordDeletionWarning(irisBot.getLogin(), warnedPastGrace); // protected bot -> keep

        final List<String> logins = userRepository.findNotEnrolledUserLoginsToDelete(graceCutoff);

        assertThat(logins).contains(due.getLogin());
        assertThat(logins).doesNotContain(withinGrace.getLogin(), loggedInSince.getLogin(), notWarned.getLogin(), irisBot.getLogin());
    }

    /**
     * Verifies that the warning is cleared for users who "came back" (re-enrolled or logged in after being warned) while
     * being kept for those who are still not-enrolled and inactive.
     */
    @Test
    void testClearDeletionWarningForReturnedUsers() {
        final Instant longAgo = ZonedDateTime.now().minusYears(1).toInstant();
        final Instant warned = ZonedDateTime.now().minusDays(10).toInstant();

        User stillInactive = createUser(TEST_PREFIX + "clrinactive", false, Set.of(), false, longAgo);
        userActivityService.recordDeletionWarning(stillInactive.getLogin(), warned);
        User reEnrolled = createUser(TEST_PREFIX + "clrenrolled", true, Set.of(), false, longAgo);
        userActivityService.recordDeletionWarning(reEnrolled.getLogin(), warned);
        User loggedInSince = createUser(TEST_PREFIX + "clrloggedin", false, Set.of(), false, ZonedDateTime.now().toInstant());
        userActivityService.recordDeletionWarning(loggedInSince.getLogin(), warned);

        userActivityService.clearDeletionWarningForReturnedUsers();

        assertThat(userActivityService.findDeletionWarningSentDate(stillInactive.getId())).isNotNull();
        assertThat(userActivityService.findDeletionWarningSentDate(reEnrolled.getId())).isNull();
        assertThat(userActivityService.findDeletionWarningSentDate(loggedInSince.getId())).isNull();
    }

    /**
     * Creates a not-enrolled-user-cleanup test fixture: a saved user with the given authorities/deleted flag and a
     * backdated last login date (set via a bulk update so it is the effective activity signal). An enrolled user is
     * given a course role, which is what "enrolled" means since course group names were replaced by user course roles.
     */
    private User createUser(String login, boolean enrolled, Set<Authority> authorities, boolean deleted, Instant lastLoginDate) {
        User user = userUtilService.createAndSaveUser(login);
        user.setAuthorities(authorities);
        user.setDeleted(deleted);
        user = userRepository.save(user);
        if (enrolled) {
            userUtilService.enrollUserInCourse(user, courseUtilService.createCourse(), CourseRole.STUDENT);
        }
        userActivityService.recordLogin(user.getLogin(), lastLoginDate);
        return user;
    }

    private User irisBot(Instant lastLoginDate) {
        User irisBot = userRepository.findOneByLogin(User.IRIS_BOT_LOGIN).orElseGet(() -> userUtilService.createAndSaveUser(User.IRIS_BOT_LOGIN));
        irisBot.setAuthorities(Set.of());
        irisBot.setDeleted(false);
        irisBot = userRepository.save(irisBot);
        userActivityService.recordLogin(irisBot.getLogin(), lastLoginDate);
        return irisBot;
    }

    @Test
    void testIsSuperAdmin() {
        // Create a super admin user
        userUtilService.addSuperAdmin(TEST_PREFIX);
        User superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

        // Create a regular admin user
        User admin = userUtilService.createAndSaveUser(TEST_PREFIX + "admin");
        admin.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        admin = userRepository.save(admin);

        User inactiveSuperAdmin = userUtilService.createAndSaveUser(TEST_PREFIX + "inactivesuperadmin");
        inactiveSuperAdmin.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY));
        inactiveSuperAdmin.setActivated(false);
        inactiveSuperAdmin = userRepository.save(inactiveSuperAdmin);

        User deletedSuperAdmin = userUtilService.createAndSaveUser(TEST_PREFIX + "deletedsuperadmin");
        deletedSuperAdmin.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY));
        deletedSuperAdmin.setDeleted(true);
        deletedSuperAdmin = userRepository.save(deletedSuperAdmin);

        // Create a regular user
        User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser");

        // Test that super admin is correctly identified
        assertThat(userRepository.isSuperAdmin(superAdmin.getLogin())).isTrue();

        // Test that regular admin is not identified as super admin
        assertThat(userRepository.isSuperAdmin(admin.getLogin())).isFalse();
        assertThat(userRepository.isSuperAdmin(inactiveSuperAdmin.getLogin())).isFalse();
        assertThat(userRepository.isSuperAdmin(deletedSuperAdmin.getLogin())).isFalse();

        // Test that regular user is not identified as super admin
        assertThat(userRepository.isSuperAdmin(regularUser.getLogin())).isFalse();

        // Test with non-existent user
        assertThat(userRepository.isSuperAdmin("nonexistentuser")).isFalse();
    }

    @Test
    void testIsAdmin() {
        // Create a super admin user
        userUtilService.addSuperAdmin(TEST_PREFIX);
        User superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

        // Create a regular admin user
        User admin = userUtilService.createAndSaveUser(TEST_PREFIX + "admin");
        admin.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        admin.setActivated(true);
        admin.setDeleted(false);
        admin = userRepository.save(admin);

        // Create an inactive admin user. Inactive accounts must not receive administrative access.
        User inactiveAdmin = userUtilService.createAndSaveUser(TEST_PREFIX + "inactiveadmin");
        inactiveAdmin.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        inactiveAdmin.setActivated(false);
        inactiveAdmin = userRepository.save(inactiveAdmin);

        User deletedAdmin = userUtilService.createAndSaveUser(TEST_PREFIX + "deletedadmin");
        deletedAdmin.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        deletedAdmin.setDeleted(true);
        deletedAdmin = userRepository.save(deletedAdmin);

        // Create a regular user
        User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser");

        // Test that only active, non-deleted administrator accounts are identified as administrators.
        assertThat(userRepository.isAdmin(superAdmin.getLogin())).isTrue();
        assertThat(userRepository.isAdmin(admin.getLogin())).isTrue();
        assertThat(userRepository.isAdmin(inactiveAdmin.getLogin())).isFalse();
        assertThat(userRepository.isAdmin(deletedAdmin.getLogin())).isFalse();

        // Test that regular user is not identified as admin
        assertThat(userRepository.isAdmin(regularUser.getLogin())).isFalse();

        // Test with non-existent user
        assertThat(userRepository.isAdmin("nonexistentuser")).isFalse();
    }

    @Test
    void testFindAllActiveAdminLogins() {
        // Create a super admin user
        userUtilService.addSuperAdmin(TEST_PREFIX);
        User superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

        // Create regular admin users
        List<User> admins = userUtilService.generateActivatedUsers(TEST_PREFIX, passwordService.hashPassword(USER_PASSWORD), Set.of(Authority.ADMIN_AUTHORITY), 1, 2);
        admins = userRepository.saveAllOrUpdate(admins);

        // Create an inactive admin user (should not be included)
        User inactiveAdmin = userUtilService.createAndSaveUser(TEST_PREFIX + "inactiveadmin");
        inactiveAdmin.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        inactiveAdmin.setActivated(false);
        inactiveAdmin = userRepository.save(inactiveAdmin);

        // Create a deleted admin user (should not be included)
        User deletedAdmin = userUtilService.createAndSaveUser(TEST_PREFIX + "deletedadmin");
        deletedAdmin.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        deletedAdmin.setActivated(true);
        deletedAdmin.setDeleted(true);
        deletedAdmin = userRepository.save(deletedAdmin);

        // Create a regular user (should not be included)
        User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser");

        final Set<String> actual = userRepository.findAllActiveAdminLogins();

        // Should contain both super admin and regular admins
        assertThat(actual).contains(superAdmin.getLogin());
        assertThat(actual).containsAll(admins.stream().map(User::getLogin).toList());

        // Should not contain inactive, deleted, or regular users
        assertThat(actual).doesNotContain(inactiveAdmin.getLogin());
        assertThat(actual).doesNotContain(deletedAdmin.getLogin());
        assertThat(actual).doesNotContain(regularUser.getLogin());
    }

    @Test
    void testAdminAuthoritiesDoNotCountAsCourseMembership() {
        // Create a super admin user
        userUtilService.addSuperAdmin(TEST_PREFIX);
        User superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

        // Create a regular admin user
        User admin = userUtilService.createAndSaveUser(TEST_PREFIX + "admin");
        admin.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        admin = userRepository.save(admin);

        // Create a course without the super admin or admin being enrolled
        Course course = courseUtilService.createCourse();

        // Create a regular user who is not enrolled
        User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser");

        // Super administrator authority alone does not grant resource membership
        assertThat(userRepository.isAtLeastStudentInCourse(superAdmin.getLogin(), course.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInCourse(superAdmin.getLogin(), course.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInCourse(superAdmin.getLogin(), course.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInCourse(superAdmin.getLogin(), course.getId())).isFalse();

        // Administrator authority alone does not grant resource membership
        assertThat(userRepository.isAtLeastStudentInCourse(admin.getLogin(), course.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInCourse(admin.getLogin(), course.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInCourse(admin.getLogin(), course.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInCourse(admin.getLogin(), course.getId())).isFalse();

        // Verify regular user does not have access
        assertThat(userRepository.isAtLeastStudentInCourse(regularUser.getLogin(), course.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInCourse(regularUser.getLogin(), course.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInCourse(regularUser.getLogin(), course.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInCourse(regularUser.getLogin(), course.getId())).isFalse();
    }

    @Test
    void testAdminAuthoritiesDoNotCountAsExerciseMembership() {
        // Create a super admin user
        userUtilService.addSuperAdmin(TEST_PREFIX);
        User superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

        // Create a regular admin user
        User admin = userUtilService.createAndSaveUser(TEST_PREFIX + "admin");
        admin.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        admin = userRepository.save(admin);

        // Create a course and exercise without the super admin or admin being enrolled
        Course course = courseUtilService.addEnrolledCourseWithModelingAndTextExercise(TEST_PREFIX);
        Exercise exercise = course.getExercises().iterator().next();

        // Create a regular user who is not enrolled
        User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser");

        // Super administrator authority alone does not grant resource membership
        assertThat(userRepository.isAtLeastStudentInExercise(superAdmin.getLogin(), exercise.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInExercise(superAdmin.getLogin(), exercise.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInExercise(superAdmin.getLogin(), exercise.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInExercise(superAdmin.getLogin(), exercise.getId())).isFalse();

        // Administrator authority alone does not grant resource membership
        assertThat(userRepository.isAtLeastStudentInExercise(admin.getLogin(), exercise.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInExercise(admin.getLogin(), exercise.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInExercise(admin.getLogin(), exercise.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInExercise(admin.getLogin(), exercise.getId())).isFalse();

        // Verify regular user does not have access
        assertThat(userRepository.isAtLeastStudentInExercise(regularUser.getLogin(), exercise.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInExercise(regularUser.getLogin(), exercise.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInExercise(regularUser.getLogin(), exercise.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInExercise(regularUser.getLogin(), exercise.getId())).isFalse();
    }

    @Test
    void testAdminAuthoritiesDoNotCountAsParticipationMembership() {
        // Create a super admin user
        userUtilService.addSuperAdmin(TEST_PREFIX);
        User superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

        // Create a regular admin user
        User admin = userUtilService.createAndSaveUser(TEST_PREFIX + "admin");
        admin.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        admin = userRepository.save(admin);

        // Create a course, exercise, and participation without the super admin or admin being enrolled
        Course course = courseUtilService.addEnrolledCourseWithModelingAndTextExercise(TEST_PREFIX);
        Exercise exercise = course.getExercises().iterator().next();
        User student = userUtilService.createAndSaveUser(TEST_PREFIX + "student");
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, student.getLogin());

        // Create a regular user who is not enrolled
        User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser");

        // Super administrator authority alone does not grant resource membership
        assertThat(userRepository.isAtLeastStudentInParticipation(superAdmin.getLogin(), participation.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInParticipation(superAdmin.getLogin(), participation.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInParticipation(superAdmin.getLogin(), participation.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInParticipation(superAdmin.getLogin(), participation.getId())).isFalse();

        // Administrator authority alone does not grant resource membership
        assertThat(userRepository.isAtLeastStudentInParticipation(admin.getLogin(), participation.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInParticipation(admin.getLogin(), participation.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInParticipation(admin.getLogin(), participation.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInParticipation(admin.getLogin(), participation.getId())).isFalse();

        // Verify regular user does not have access
        assertThat(userRepository.isAtLeastStudentInParticipation(regularUser.getLogin(), participation.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInParticipation(regularUser.getLogin(), participation.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInParticipation(regularUser.getLogin(), participation.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInParticipation(regularUser.getLogin(), participation.getId())).isFalse();
    }

    @Test
    void testAdminAuthoritiesDoNotCountAsLectureMembership() {
        // Create a super admin user
        userUtilService.addSuperAdmin(TEST_PREFIX);
        User superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

        // Create a regular admin user
        User admin = userUtilService.createAndSaveUser(TEST_PREFIX + "admin");
        admin.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        admin = userRepository.save(admin);

        // Create a course and lecture without the super admin or admin being enrolled
        Course course = courseUtilService.createCourse();
        Lecture lecture = lectureUtilService.createLecture(course);

        // Create a regular user who is not enrolled
        User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser");

        // Super administrator authority alone does not grant resource membership
        assertThat(userRepository.isAtLeastStudentInLecture(superAdmin.getLogin(), lecture.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInLecture(superAdmin.getLogin(), lecture.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInLecture(superAdmin.getLogin(), lecture.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInLecture(superAdmin.getLogin(), lecture.getId())).isFalse();

        // Administrator authority alone does not grant resource membership
        assertThat(userRepository.isAtLeastStudentInLecture(admin.getLogin(), lecture.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInLecture(admin.getLogin(), lecture.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInLecture(admin.getLogin(), lecture.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInLecture(admin.getLogin(), lecture.getId())).isFalse();

        // Verify regular user does not have access
        assertThat(userRepository.isAtLeastStudentInLecture(regularUser.getLogin(), lecture.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInLecture(regularUser.getLogin(), lecture.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInLecture(regularUser.getLogin(), lecture.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInLecture(regularUser.getLogin(), lecture.getId())).isFalse();
    }

    @Test
    void testAdminAuthoritiesDoNotCountAsLectureUnitMembership() {
        // Create a super admin user
        userUtilService.addSuperAdmin(TEST_PREFIX);
        User superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

        // Create a regular admin user
        User admin = userUtilService.createAndSaveUser(TEST_PREFIX + "admin");
        admin.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        admin = userRepository.save(admin);

        // Create a course, lecture, and lecture unit without the super admin or admin being enrolled
        Course course = courseUtilService.createCourse();
        Lecture lecture = lectureUtilService.createLecture(course);
        LectureUnit lectureUnit = lectureUtilService.createTextUnit(lecture);

        // Create a regular user who is not enrolled
        User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser");

        // Super administrator authority alone does not grant resource membership
        assertThat(userRepository.isAtLeastStudentInLectureUnit(superAdmin.getLogin(), lectureUnit.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInLectureUnit(superAdmin.getLogin(), lectureUnit.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInLectureUnit(superAdmin.getLogin(), lectureUnit.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInLectureUnit(superAdmin.getLogin(), lectureUnit.getId())).isFalse();

        // Administrator authority alone does not grant resource membership
        assertThat(userRepository.isAtLeastStudentInLectureUnit(admin.getLogin(), lectureUnit.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInLectureUnit(admin.getLogin(), lectureUnit.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInLectureUnit(admin.getLogin(), lectureUnit.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInLectureUnit(admin.getLogin(), lectureUnit.getId())).isFalse();

        // Verify regular user does not have access
        assertThat(userRepository.isAtLeastStudentInLectureUnit(regularUser.getLogin(), lectureUnit.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInLectureUnit(regularUser.getLogin(), lectureUnit.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInLectureUnit(regularUser.getLogin(), lectureUnit.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInLectureUnit(regularUser.getLogin(), lectureUnit.getId())).isFalse();
    }

    @Test
    void testIsInternalUserByLoginAndByEmail() {
        User internalUser = userUtilService.createAndSaveUser(TEST_PREFIX + "internal");

        User externalUser = UserFactory.generateActivatedUser(TEST_PREFIX + "external");
        externalUser.setInternal(false);
        externalUser = userRepository.save(externalUser);

        User deletedUser = UserFactory.generateActivatedUser(TEST_PREFIX + "deleted");
        deletedUser.setDeleted(true);
        deletedUser = userRepository.save(deletedUser);

        assertThat(userRepository.isInternalUserByLogin(internalUser.getLogin())).contains(true);
        assertThat(userRepository.isInternalUserByLogin(externalUser.getLogin())).contains(false);
        assertThat(userRepository.isInternalUserByLogin(TEST_PREFIX + "doesnotexist")).isEmpty();
        // soft-deleted users must not be reported, following the convention documented on this repository
        assertThat(userRepository.isInternalUserByLogin(deletedUser.getLogin())).isEmpty();

        assertThat(userRepository.isInternalUserByEmailIgnoreCase(internalUser.getEmail())).contains(true);
        assertThat(userRepository.isInternalUserByEmailIgnoreCase(externalUser.getEmail())).contains(false);
        assertThat(userRepository.isInternalUserByEmailIgnoreCase(TEST_PREFIX + "doesnotexist@test.de")).isEmpty();
        assertThat(userRepository.isInternalUserByEmailIgnoreCase(deletedUser.getEmail())).isEmpty();
        // the lookup by email has to stay case-insensitive, just like the entity based findOneByEmailIgnoreCase it replaces
        assertThat(userRepository.isInternalUserByEmailIgnoreCase(internalUser.getEmail().toUpperCase(Locale.ROOT))).contains(true);
    }

    /**
     * The two addresses differ only in case, and the account that is written second is refused. Case folding happens in
     * {@link User#canonicalEmail}, not in the index, which is what the migration lowercases the existing rows for: on
     * PostgreSQL a unique index compares the stored strings as they are.
     */
    @Test
    void testEmailIsUniqueIgnoringCase() {
        User firstUser = UserFactory.generateActivatedUser(TEST_PREFIX + "uniqueemail1");
        firstUser.setEmail("Unique.Email@Example.COM");
        userRepository.saveAndFlush(firstUser);

        User secondUser = UserFactory.generateActivatedUser(TEST_PREFIX + "uniqueemail2");
        secondUser.setEmail("unique.email@example.com");

        assertThatThrownBy(() -> userRepository.saveAndFlush(secondUser)).isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * An account may have no email address, and a unique index that counted those as equal would let only one such
     * account exist. A blank address is stored as {@code null} and must not collide either.
     */
    @Test
    void testAccountsWithoutEmailDoNotCollide() {
        User withoutEmail = UserFactory.generateActivatedUser(TEST_PREFIX + "noemail1");
        withoutEmail.setEmail(null);
        userRepository.saveAndFlush(withoutEmail);

        User withBlankEmail = UserFactory.generateActivatedUser(TEST_PREFIX + "noemail2");
        withBlankEmail.setEmail("   ");
        userRepository.saveAndFlush(withBlankEmail);

        assertThat(userRepository.findById(withoutEmail.getId())).get().extracting(User::getEmail).isNull();
        assertThat(userRepository.findById(withBlankEmail.getId())).get().extracting(User::getEmail).isNull();
    }

    /**
     * The active user metrics exclude test users in Java instead of joining {@code jhi_user} into the (very hot)
     * submission aggregation, so the ids of all test users have to be retrievable on their own.
     */
    @Test
    void testFindAllTestUserIdsReturnsOnlyTestUsers() {
        User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regular");
        User testUser = UserFactory.generateActivatedUser(TEST_PREFIX + "flagged");
        testUser.setTestUser(true);
        testUser = userRepository.save(testUser);

        Set<Long> testUserIds = userRepository.findAllTestUserIds();

        assertThat(testUserIds).contains(testUser.getId()).doesNotContain(regularUser.getId());
    }

    /**
     * Soft-deleted test users stay in the exclusion set: they must never be counted as active users, and keeping them
     * avoids a second predicate on the hot path.
     */
    @Test
    void testFindAllTestUserIdsIncludesSoftDeletedTestUsers() {
        User deletedTestUser = UserFactory.generateActivatedUser(TEST_PREFIX + "deletedflagged");
        deletedTestUser.setTestUser(true);
        deletedTestUser.setDeleted(true);
        deletedTestUser = userRepository.save(deletedTestUser);

        Set<Long> testUserIds = userRepository.findAllTestUserIds();

        assertThat(testUserIds).contains(deletedTestUser.getId());
    }
}
