package de.tum.in.www1.artemis.repository.tutorialgroups;

import java.util.Optional;
import java.util.Set;

import javax.transaction.Transactional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface TutorialGroupRepository extends JpaRepository<TutorialGroup, Long> {

    @Query("""
            SELECT tutorialGroup.title
            FROM TutorialGroup tutorialGroup
            WHERE tutorialGroup.id = :tutorialGroupId
            """)
    @Cacheable(cacheNames = "tutorialGroupTitle", key = "#tutorialGroupId", unless = "#result == null")
    Optional<String> getTutorialGroupTitle(@Param("tutorialGroupId") Long tutorialGroupId);

    @Query("""
            SELECT DISTINCT tutorialGroup.campus
            FROM TutorialGroup tutorialGroup
            WHERE tutorialGroup.course.id = :#{#courseId} AND tutorialGroup.campus IS NOT NULL""")
    Set<String> findAllUniqueCampusValuesInCourse(@Param("courseId") Long courseId);

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
            WHERE tutorialGroup.course.id = :#{#courseId}
            ORDER BY tutorialGroup.title""")
    Set<TutorialGroup> findAllByCourseId(@Param("courseId") Long courseId);

    boolean existsByTitleAndCourse(String title, Course course);

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
            LEFT JOIN FETCH tutorialGroup.teachingAssistant
            LEFT JOIN FETCH tutorialGroup.registrations
            WHERE tutorialGroup.course.id = :#{#courseId}
            ORDER BY tutorialGroup.title""")
    Set<TutorialGroup> findAllByCourseIdWithTeachingAssistantAndRegistrations(@Param("courseId") Long courseId);

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
            LEFT JOIN FETCH tutorialGroup.teachingAssistant
            LEFT JOIN FETCH tutorialGroup.registrations
            LEFT JOIN FETCH tutorialGroup.tutorialGroupSessions
            LEFT JOIN FETCH tutorialGroup.tutorialGroupSchedule
            WHERE tutorialGroup.id = :#{#tutorialGroupId}
            """)
    Optional<TutorialGroup> findByIdWithTeachingAssistantAndRegistrationsAndSessions(@Param("tutorialGroupId") long tutorialGroupId);

    Set<TutorialGroup> findAllByTeachingAssistant(User teachingAssistant);

    @Modifying
    @Transactional
    // ok because of delete
    void deleteAllByCourse(Course course);

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
            LEFT JOIN FETCH tutorialGroup.tutorialGroupSessions
            WHERE tutorialGroup.id = :#{#tutorialGroupId}
            """)
    Optional<TutorialGroup> findByIdWithSessions(@Param("tutorialGroupId") long tutorialGroupId);

    default TutorialGroup findByIdWithSessionsElseThrow(long tutorialGroupId) {
        return findByIdWithSessions(tutorialGroupId).orElseThrow(() -> new EntityNotFoundException("TutorialGroup", tutorialGroupId));
    }

    default TutorialGroup findByIdElseThrow(long tutorialGroupId) {
        return this.findById(tutorialGroupId).orElseThrow(() -> new EntityNotFoundException("TutorialGroup", tutorialGroupId));
    }

    default TutorialGroup findByIdWithTeachingAssistantAndRegistrationsElseThrow(long tutorialGroupId) {
        return this.findByIdWithTeachingAssistantAndRegistrationsAndSessions(tutorialGroupId).orElseThrow(() -> new EntityNotFoundException("TutorialGroup", tutorialGroupId));
    }
}
