package de.tum.cit.aet.artemis.core.test_repository;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;

@Lazy
@Repository
@Primary
public interface UserCourseRoleTestRepository extends UserCourseRoleRepository {

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
