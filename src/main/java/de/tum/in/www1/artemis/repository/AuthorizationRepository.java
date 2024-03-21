package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.User;

@Profile(PROFILE_CORE)
@Repository
public interface AuthorizationRepository extends JpaRepository<User, Long> {

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Course course
            ON user.login = :login
                AND course.id = :courseId
            WHERE course.studentGroupName MEMBER OF user.groups
            """)
    boolean isStudentInCourse(@Param("login") String login, @Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Course course
            ON user.login = :login
                AND course.id = :courseId
            WHERE course.teachingAssistantGroupName MEMBER OF user.groups
            """)
    boolean isTeachingAssistantInCourse(@Param("login") String login, @Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Course course
            ON user.login = :login
                AND course.id = :courseId
            WHERE course.editorGroupName MEMBER OF user.groups
            """)
    boolean isEditorInCourse(@Param("login") String login, @Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Course course
            ON user.login = :login
                AND course.id = :courseId
            WHERE course.instructorGroupName MEMBER OF user.groups
            """)
    boolean isInstructorInCourse(@Param("login") String login, @Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            WHERE user.login = :login
                AND :#{T(de.tum.in.www1.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities
            """)
    boolean isAdmin(@Param("login") String login);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Course course
            ON user.login = :login
                AND course.id = :courseId
            WHERE (course.studentGroupName MEMBER OF user.groups)
                    OR (course.teachingAssistantGroupName MEMBER OF user.groups)
                    OR (course.editorGroupName MEMBER OF user.groups)
                    OR (course.instructorGroupName MEMBER OF user.groups)
                    OR (:#{T(de.tum.in.www1.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastStudentInCourse(@Param("login") String login, @Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Course course
            ON user.login = :login
                AND course.id = :courseId
            WHERE (course.teachingAssistantGroupName MEMBER OF user.groups)
                    OR (course.editorGroupName MEMBER OF user.groups)
                    OR (course.instructorGroupName MEMBER OF user.groups)
                    OR (:#{T(de.tum.in.www1.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastTeachingAssistantInCourse(@Param("login") String login, @Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Course course
            ON user.login = :login
                AND course.id = :courseId
            WHERE (course.editorGroupName MEMBER OF user.groups)
                    OR (course.instructorGroupName MEMBER OF user.groups)
                    OR (:#{T(de.tum.in.www1.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastEditorInCourse(@Param("login") String login, @Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Course course
            ON user.login = :login
                AND course.id = :courseId
            WHERE (course.instructorGroupName MEMBER OF user.groups)
                OR (:#{T(de.tum.in.www1.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastInstructorInCourse(@Param("login") String login, @Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Exercise exercise
            ON user.login = :login
                AND exercise.id = :exerciseId
            INNER JOIN exercise.course course
            WHERE (course.studentGroupName MEMBER OF user.groups)
                    OR (course.teachingAssistantGroupName MEMBER OF user.groups)
                    OR (course.editorGroupName MEMBER OF user.groups)
                    OR (course.instructorGroupName MEMBER OF user.groups)
                    OR (:#{T(de.tum.in.www1.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastStudentForExercise(@Param("login") String login, @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Exercise exercise
            ON user.login = :login
                AND exercise.id = :exerciseId
            INNER JOIN exercise.course course
            WHERE (course.teachingAssistantGroupName MEMBER OF user.groups)
                    OR (course.editorGroupName MEMBER OF user.groups)
                    OR (course.instructorGroupName MEMBER OF user.groups)
                    OR (:#{T(de.tum.in.www1.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastTeachingAssistantForExercise(@Param("login") String login, @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Exercise exercise
            ON user.login = :login
                AND exercise.id = :exerciseId
            INNER JOIN exercise.course course
            WHERE (course.editorGroupName MEMBER OF user.groups)
                    OR (course.instructorGroupName MEMBER OF user.groups)
                    OR (:#{T(de.tum.in.www1.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastEditorForExercise(@Param("login") String login, @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Exercise exercise
            ON user.login = :login
                AND exercise.id = :exerciseId
            INNER JOIN exercise.course course
            WHERE (course.instructorGroupName MEMBER OF user.groups)
                    OR (:#{T(de.tum.in.www1.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastInstructorForExercise(@Param("login") String login, @Param("exerciseId") long exerciseId);
}
