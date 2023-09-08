package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.CourseCodeOfConduct;

@Repository
public interface CourseCodeOfConductRepository extends JpaRepository<CourseCodeOfConduct, Long> {

    @Query("""
            SELECT c
            FROM CourseCodeOfConduct c
            WHERE c.course.id = :courseId AND c.user.id = :userId
            """)
    Optional<CourseCodeOfConduct> findByCourseIdAndUserId(@Param("courseId") Long courseId, @Param("userId") Long userId);
}
