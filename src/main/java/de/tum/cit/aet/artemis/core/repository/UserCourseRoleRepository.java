package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface UserCourseRoleRepository extends ArtemisJpaRepository<UserCourseRole, UserCourseRole.UserCourseRoleId> {

    /**
     * Returns the (non-deleted) users holding the given role in the given course. Selects the {@link User} directly so
     * callers do not pay the hydration cost of the intermediate {@link UserCourseRole} wrapper entities.
     *
     * @param courseId the id of the course
     * @param role     the role to filter by
     * @return the set of non-deleted users with that role in the course
     */
    @Query("SELECT ucr.user FROM UserCourseRole ucr WHERE ucr.course.id = :courseId AND ucr.role = :role AND ucr.user.deleted = FALSE")
    Set<User> findUsersByCourse_IdAndRole(@Param("courseId") Long courseId, @Param("role") CourseRole role);

    /**
     * Batch variant of {@link #findUsersByCourse_IdAndRole} that matches any of the given roles, e.g. to fetch all
     * staff (teaching assistants, editors, instructors) of a course in a single query.
     *
     * @param courseId the id of the course
     * @param roles    the roles to match
     * @return the set of non-deleted users holding any of the given roles in the course
     */
    @Query("SELECT DISTINCT ucr.user FROM UserCourseRole ucr WHERE ucr.course.id = :courseId AND ucr.role IN :roles AND ucr.user.deleted = FALSE")
    Set<User> findUsersByCourse_IdAndRoleIn(@Param("courseId") Long courseId, @Param("roles") Collection<CourseRole> roles);

    @Query("SELECT EXISTS (FROM UserCourseRole ucr WHERE ucr.user.id = :userId AND ucr.course.id = :courseId AND ucr.role = :role)")
    boolean existsByUser_IdAndCourse_IdAndRole(@Param("userId") Long userId, @Param("courseId") Long courseId, @Param("role") CourseRole role);

    /**
     * Batch variant of {@link #existsByUser_IdAndCourse_IdAndRole}: returns which of the given users already hold the
     * role in the course in a single query, so bulk enrollment can skip the already-enrolled subset without one
     * existence check per user.
     *
     * @param courseId the id of the course
     * @param role     the role to check for
     * @param userIds  the candidate user ids
     * @return the subset of userIds that already hold the role in the course
     */
    @Query("SELECT ucr.user.id FROM UserCourseRole ucr WHERE ucr.course.id = :courseId AND ucr.role = :role AND ucr.user.id IN :userIds")
    Set<Long> findUserIdsByCourse_IdAndRoleAndUser_IdIn(@Param("courseId") Long courseId, @Param("role") CourseRole role, @Param("userIds") Collection<Long> userIds);

    /**
     * Returns which of the given users hold the given role in any course, in a single query. Used to batch-rebuild
     * global authorities for many users at once instead of one query per user.
     *
     * @param userIds the candidate user ids
     * @param role    the role to check for
     * @return the subset of userIds that hold the role in at least one course
     */
    @Query("SELECT DISTINCT ucr.user.id FROM UserCourseRole ucr WHERE ucr.user.id IN :userIds AND ucr.role = :role")
    Set<Long> findUserIdsByUser_IdInAndRole(@Param("userIds") Collection<Long> userIds, @Param("role") CourseRole role);

    @Query("SELECT EXISTS (FROM UserCourseRole ucr WHERE ucr.user.id = :userId AND ucr.course.id = :courseId AND ucr.role IN :roles)")
    boolean existsByUser_IdAndCourse_IdAndRoleIn(@Param("userId") Long userId, @Param("courseId") Long courseId, @Param("roles") Collection<CourseRole> roles);

    @Query("SELECT EXISTS (FROM UserCourseRole ucr WHERE ucr.user.id = :userId AND ucr.role IN :roles)")
    boolean existsByUser_IdAndRoleIn(@Param("userId") Long userId, @Param("roles") Collection<CourseRole> roles);

    /**
     * Returns the IDs of users who have the given role in the given course, restricted to the provided user ID set.
     *
     * @param courseId the course to check
     * @param role     the course role to filter by
     * @param userIds  the candidate user IDs to check
     * @return set of user IDs that already have the given role in the given course
     */
    @Query("""
                SELECT ucr.user.id FROM UserCourseRole ucr
                WHERE ucr.course.id = :courseId
                  AND ucr.role = :role
                  AND ucr.user.id IN :userIds
            """)
    Set<Long> findUserIdsByCourseIdAndRoleAndUserIdIn(@Param("courseId") long courseId, @Param("role") CourseRole role, @Param("userIds") Collection<Long> userIds);

    @Transactional
    @Modifying
    @Query("DELETE FROM UserCourseRole ucr WHERE ucr.user.id = :userId AND ucr.course.id = :courseId AND ucr.role = :role")
    void deleteByUser_IdAndCourse_IdAndRole(@Param("userId") Long userId, @Param("courseId") Long courseId, @Param("role") CourseRole role);

    @Transactional
    @Modifying
    @Query("DELETE FROM UserCourseRole ucr WHERE ucr.course.id = :courseId AND ucr.role IN :roles")
    void deleteByCourse_IdAndRoleIn(@Param("courseId") Long courseId, @Param("roles") Collection<CourseRole> roles);

    @Transactional
    @Modifying
    @Query("DELETE FROM UserCourseRole ucr WHERE ucr.user.id = :userId")
    void deleteByUser_Id(@Param("userId") Long userId);

}
