package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TutorialGroup;

@Repository
public interface TutorialGroupRepository extends JpaRepository<TutorialGroup, Long> {

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
            LEFT JOIN FETCH tutorialGroup.teachingAssistant ta
            LEFT JOIN FETCH tutorialGroup.registeredStudents s
            WHERE tutorialGroup.course.id = :#{#courseId}
            ORDER BY tutorialGroup.title""")
    Set<TutorialGroup> findAllByCourseIdWithTeachingAssistantAndRegisteredStudents(@Param("courseId") Long courseId);
}
