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

    boolean existsByUser_IdAndCourse_IdAndRole(Long userId, Long courseId, CourseRole role);

    boolean existsByUser_IdAndRoleIn(Long userId, Collection<CourseRole> roles);

    @Transactional
    @Modifying
    void deleteByUser_IdAndCourse_IdAndRole(Long userId, Long courseId, CourseRole role);

    @Transactional
    @Modifying
    void deleteByUser_IdAndCourse_Id(Long userId, Long courseId);

    @Transactional
    @Modifying
    void deleteByUser_Id(Long userId);

    @Transactional
    @Modifying
    void deleteByCourse_IdAndRoleIn(Long courseId, Collection<CourseRole> roles);

}
