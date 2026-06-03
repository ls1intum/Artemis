package de.tum.cit.aet.artemis.core.repository;

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

import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface UserCourseRoleRepository extends ArtemisJpaRepository<UserCourseRole, UserCourseRole.UserCourseRoleId> {

    @Query("SELECT ucr FROM UserCourseRole ucr JOIN FETCH ucr.user WHERE ucr.course.id = :courseId AND ucr.role = :role")
    List<UserCourseRole> findByCourse_IdAndRole(@Param("courseId") Long courseId, @Param("role") CourseRole role);

    @Query("""
                SELECT CASE WHEN COUNT(ucr) > 0 THEN TRUE ELSE FALSE END
                FROM UserCourseRole ucr
                WHERE ucr.user.id = :userId AND ucr.course.id = :courseId AND ucr.role = :role
            """)
    boolean existsByUser_IdAndCourse_IdAndRole(@Param("userId") Long userId, @Param("courseId") Long courseId, @Param("role") CourseRole role);

    @Query("""
                SELECT CASE WHEN COUNT(ucr) > 0 THEN TRUE ELSE FALSE END
                FROM UserCourseRole ucr
                WHERE ucr.user.id = :userId AND ucr.role IN :roles
            """)
    boolean existsByUser_IdAndRoleIn(@Param("userId") Long userId, @Param("roles") Collection<CourseRole> roles);

    @Transactional
    @Modifying
    @Query("DELETE FROM UserCourseRole ucr WHERE ucr.user.id = :userId AND ucr.course.id = :courseId AND ucr.role = :role")
    void deleteByUser_IdAndCourse_IdAndRole(@Param("userId") Long userId, @Param("courseId") Long courseId, @Param("role") CourseRole role);

    @Transactional
    @Modifying
    @Query("DELETE FROM UserCourseRole ucr WHERE ucr.user.id = :userId AND ucr.course.id = :courseId")
    void deleteByUser_IdAndCourse_Id(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Transactional
    @Modifying
    @Query("DELETE FROM UserCourseRole ucr WHERE ucr.user.id = :userId")
    void deleteByUser_Id(@Param("userId") Long userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM UserCourseRole ucr WHERE ucr.course.id = :courseId AND ucr.role IN :roles")
    void deleteByCourse_IdAndRoleIn(@Param("courseId") Long courseId, @Param("roles") Collection<CourseRole> roles);

    @Transactional
    @Modifying
    @Query("DELETE FROM UserCourseRole ucr WHERE ucr.course.id = :courseId")
    void deleteByCourse_Id(@Param("courseId") Long courseId);

    @Transactional
    @Modifying
    @Query("DELETE FROM UserCourseRole ucr")
    void deleteAllInBulk();

}
