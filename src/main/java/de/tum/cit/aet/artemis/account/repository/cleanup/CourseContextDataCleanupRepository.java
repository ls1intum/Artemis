package de.tum.cit.aet.artemis.account.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Removes what places a user inside a course: their roles, exam registrations, tutorial group membership and the
 * requests and subscriptions they made.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 *
 * <p>
 * Teaching a tutorial group is only detached, because the group carries on without the assistant who ran it.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CourseContextDataCleanupRepository extends ArtemisJpaRepository<UserCourseRole, Long> {

    @Query("""
            SELECT role.user.id AS userId, COUNT(role) AS count
            FROM UserCourseRole role
            WHERE role.user.id IN :userIds
            GROUP BY role.user.id
            """)
    List<UserReferenceCount> countCourseRoles(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM UserCourseRole role
            WHERE role.user.id = :userId
            """)
    int deleteCourseRoles(@Param("userId") long userId);

    @Query("""
            SELECT request.requester.id AS userId, COUNT(request) AS count
            FROM CourseRequest request
            WHERE request.requester.id IN :userIds
            GROUP BY request.requester.id
            """)
    List<UserReferenceCount> countCourseRequests(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM CourseRequest request
            WHERE request.requester.id = :userId
            """)
    int deleteCourseRequests(@Param("userId") long userId);

    @Query("""
            SELECT token.user.id AS userId, COUNT(token) AS count
            FROM CalendarSubscriptionTokenStore token
            WHERE token.user.id IN :userIds
            GROUP BY token.user.id
            """)
    List<UserReferenceCount> countCalendarSubscriptions(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM CalendarSubscriptionTokenStore token
            WHERE token.user.id = :userId
            """)
    int deleteCalendarSubscriptions(@Param("userId") long userId);

    @Query("""
            SELECT examUser.user.id AS userId, COUNT(examUser) AS count
            FROM ExamUser examUser
            WHERE examUser.user.id IN :userIds
            GROUP BY examUser.user.id
            """)
    List<UserReferenceCount> countExamRegistrations(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM ExamUser examUser
            WHERE examUser.user.id = :userId
            """)
    int deleteExamRegistrations(@Param("userId") long userId);

    @Query("""
            SELECT studentExam.user.id AS userId, COUNT(studentExam) AS count
            FROM StudentExam studentExam
            WHERE studentExam.user.id IN :userIds
            GROUP BY studentExam.user.id
            """)
    List<UserReferenceCount> countStudentExams(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM StudentExam studentExam
            WHERE studentExam.user.id = :userId
            """)
    int deleteStudentExams(@Param("userId") long userId);

    @Query("""
            SELECT registration.student.id AS userId, COUNT(registration) AS count
            FROM TutorialGroupRegistration registration
            WHERE registration.student.id IN :userIds
            GROUP BY registration.student.id
            """)
    List<UserReferenceCount> countTutorialGroupRegistrations(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM TutorialGroupRegistration registration
            WHERE registration.student.id = :userId
            """)
    int deleteTutorialGroupRegistrations(@Param("userId") long userId);

    @Query("""
            SELECT tutorialGroup.teachingAssistant.id AS userId, COUNT(tutorialGroup) AS count
            FROM TutorialGroup tutorialGroup
            WHERE tutorialGroup.teachingAssistant.id IN :userIds
            GROUP BY tutorialGroup.teachingAssistant.id
            """)
    List<UserReferenceCount> countTaughtTutorialGroups(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of update
    @Query("""
            UPDATE TutorialGroup tutorialGroup
            SET tutorialGroup.teachingAssistant = NULL
            WHERE tutorialGroup.teachingAssistant.id = :userId
            """)
    int detachTaughtTutorialGroups(@Param("userId") long userId);

    /**
     * Deletes the sittings recorded for the account's exams, so that the exams themselves can be removed.
     *
     * @param userId the account being deleted
     * @return how many sittings were deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM ExamSession session
            WHERE session.studentExam.id IN (SELECT studentExam.id FROM StudentExam studentExam WHERE studentExam.user.id = :userId)
            """)
    int deleteExamSessions(@Param("userId") long userId);

    /**
     * Drops the links from the account's exams to the exercises they were made up of. The exercises stay: they belong
     * to the exam, not to the student who sat it.
     *
     * <p>
     * Addressed natively because {@code student_exam_exercise} is a join table and has no entity of its own.
     *
     * @param userId the account being deleted
     * @return how many links were deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query(nativeQuery = true, value = """
            DELETE FROM student_exam_exercise
            WHERE student_exam_id IN (SELECT id FROM student_exam WHERE user_id = :userId)
            """)
    int deleteStudentExamExerciseLinks(@Param("userId") long userId);

    /**
     * The personal images held for the account's exam registrations. Only the two paths are read: a long-serving
     * account can be registered for a great many exams, and loading every registration as an entity to reach two
     * strings is the expensive way to ask that question.
     *
     * @param userId the account being deleted
     * @return one entry per registration, either path possibly absent
     */
    @Query("""
            SELECT examUser.id AS id, examUser.signingImagePath AS signingImagePath, examUser.studentImagePath AS studentImagePath
            FROM ExamUser examUser
            WHERE examUser.user.id = :userId
            """)
    List<ExamUserImagePaths> findExamUserImagePaths(@Param("userId") long userId);
}
