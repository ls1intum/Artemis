package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.CourseCodeOfConduct;

/**
 * Spring Data repository for the Code of Conduct entity.
 */
@Repository
public interface CourseCodeOfConductRepository extends JpaRepository<CourseCodeOfConduct, Long> {

    /**
     * Find the user's agreement to a course's code of conduct.
     *
     * @param courseId the ID of the code of conduct's course
     * @param userId   the user's ID
     * @return the user's agreement to the course's code of conduct
     */
    @Query("""
            SELECT c
            FROM CourseCodeOfConduct c
            WHERE c.course.id = :courseId AND c.user.id = :userId
            """)
    Optional<CourseCodeOfConduct> findByCourseIdAndUserId(@Param("courseId") Long courseId, @Param("userId") Long userId);
}
