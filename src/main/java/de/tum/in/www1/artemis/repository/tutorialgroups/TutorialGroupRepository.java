package de.tum.in.www1.artemis.repository.tutorialgroups;

import java.util.Optional;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
            WHERE tutorialGroup.id = :#{#tutorialGroupId}
            """)
    Optional<TutorialGroup> findByIdWithTeachingAssistantAndRegistrations(@Param("tutorialGroupId") long tutorialGroupId);

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
            LEFT JOIN FETCH tutorialGroup.teachingAssistant
            LEFT JOIN FETCH tutorialGroup.registrations
            WHERE tutorialGroup.title = :#{#title}
            AND tutorialGroup.course.id = :#{#courseId}
            """)
    Optional<TutorialGroup> findByTitleAndCourseIdWithTeachingAssistantAndRegistrations(String title, Long courseId);

    boolean existsByTitleAndCourseId(String title, Long courseId);

    Set<TutorialGroup> findAllByTeachingAssistant(User teachingAssistant);

    @Modifying
    @Transactional
    void deleteAllByCourse(Course course);

    default TutorialGroup findByIdElseThrow(long tutorialGroupId) {
        return this.findById(tutorialGroupId).orElseThrow(() -> new EntityNotFoundException("TutorialGroup", tutorialGroupId));
    }

    default TutorialGroup findByIdWithTeachingAssistantAndRegistrationsElseThrow(long tutorialGroupId) {
        return this.findByIdWithTeachingAssistantAndRegistrations(tutorialGroupId).orElseThrow(() -> new EntityNotFoundException("TutorialGroup", tutorialGroupId));
    }
}
