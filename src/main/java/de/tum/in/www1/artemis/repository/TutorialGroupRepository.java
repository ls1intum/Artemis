package de.tum.in.www1.artemis.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TutorialGroup;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface TutorialGroupRepository extends JpaRepository<TutorialGroup, Long> {

    @Query("""
            SELECT tutorialGroup.title
            FROM TutorialGroup tutorialGroup
            WHERE tutorialGroup.id = :tutorialGroupId
            """)
    @Cacheable(cacheNames = "tutorialGroupTitle", key = "#tutorialGroupId", unless = "#result == null")
    String getTutorialGroupTitle(@Param("tutorialGroupId") Long tutorialGroupId);

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
            WHERE tutorialGroup.course.id = :#{#courseId}
            ORDER BY tutorialGroup.title""")
    Set<TutorialGroup> findAllByCourseId(@Param("courseId") Long courseId);

    @Query("""
            SELECT tutorialGroup
                FROM TutorialGroup tutorialGroup
                LEFT JOIN FETCH tutorialGroup.teachingAssistant
                LEFT JOIN FETCH tutorialGroup.registeredStudents
                WHERE tutorialGroup.course.id = :#{#courseId}
                ORDER BY tutorialGroup.title""")
    Set<TutorialGroup> findAllByCourseIdWithTeachingAssistantAndRegisteredStudents(@Param("courseId") Long courseId);

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
            LEFT JOIN FETCH tutorialGroup.teachingAssistant
            LEFT JOIN FETCH tutorialGroup.registeredStudents
            WHERE tutorialGroup.id = :#{#tutorialGroupId}
            """)
    Optional<TutorialGroup> findByIdWithTeachingAssistantAndRegisteredStudents(@Param("tutorialGroupId") long tutorialGroupId);

    default TutorialGroup findByIdWithTeachingAssistantAndRegisteredStudentsElseThrow(long tutorialGroupId) {
        return findByIdWithTeachingAssistantAndRegisteredStudents(tutorialGroupId).orElseThrow(() -> new EntityNotFoundException("TutorialGroup", tutorialGroupId));
    }
}
