package de.tum.cit.aet.artemis.core.test_repository;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;

@Lazy
@Repository
@Primary
public interface UserCourseRoleTestRepository extends UserCourseRoleRepository {

    @Query("SELECT ucr FROM UserCourseRole ucr JOIN FETCH ucr.user WHERE ucr.course.id = :courseId AND ucr.role = :role")
    List<UserCourseRole> findByCourse_IdAndRole(@Param("courseId") Long courseId, @Param("role") CourseRole role);

    @Transactional
    @Modifying
    @Query("DELETE FROM UserCourseRole ucr WHERE ucr.user.id = :userId AND ucr.course.id = :courseId")
    void deleteByUser_IdAndCourse_Id(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Transactional
    @Modifying
    @Query("DELETE FROM UserCourseRole ucr WHERE ucr.course.id = :courseId")
    void deleteByCourse_Id(@Param("courseId") Long courseId);

    @Transactional
    @Modifying
    @Query("DELETE FROM UserCourseRole ucr")
    void deleteAllInBulk();

}
