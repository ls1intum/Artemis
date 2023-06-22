package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.competency.LearningPath;

@Repository
public interface LearningPathRepository extends JpaRepository<LearningPath, Long> {

    List<LearningPath> findByCourseIdAndUserId(long courseId, long userId);

    @Query("""
            SELECT lp
            FROM LearningPath lp
            WHERE lp.course.id = :courseId
            """)
    Set<LearningPath> findAllForCourse(@Param("courseId") long courseId);

    @Query("""
            SELECT lp
            FROM LearningPath lp
            WHERE (lp.course.id = :courseId) AND (lp.user.login LIKE %:partialLogin%)
            """)
    Page<LearningPath> findByLoginInCourse(@Param("partialLogin") String partialLogin, @Param("courseId") long courseId, Pageable pageable);
}
