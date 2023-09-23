package de.tum.in.www1.artemis.repository.tutorialgroups;

import java.time.ZonedDateTime;
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
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface TutorialGroupRepository extends JpaRepository<TutorialGroup, Long> {

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
            LEFT JOIN FETCH tutorialGroup.tutorialGroupChannel
            WHERE tutorialGroup.id = :#{#tutorialGroupId}
            """)
    Optional<TutorialGroup> getTutorialGroupWithChannel(@Param("tutorialGroupId") Long tutorialGroupId);

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
            SELECT DISTINCT tutorialGroup.language
            FROM TutorialGroup tutorialGroup
            WHERE tutorialGroup.course.id = :#{#courseId} AND tutorialGroup.language IS NOT NULL""")
    Set<String> findAllUniqueLanguageValuesInCourse(@Param("courseId") Long courseId);

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
            WHERE tutorialGroup.course.id = :#{#courseId}
            ORDER BY tutorialGroup.title""")
    Set<TutorialGroup> findAllByCourseId(@Param("courseId") Long courseId);

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
            LEFT JOIN FETCH tutorialGroup.tutorialGroupChannel
            WHERE tutorialGroup.course.id = :#{#courseId}
            ORDER BY tutorialGroup.title""")
    Set<TutorialGroup> findAllByCourseIdWithChannel(@Param("courseId") Long courseId);

    boolean existsByTitleAndCourse(String title, Course course);

    @Query("""
            SELECT DISTINCT tutorialGroups.id
            FROM Course c
                LEFT JOIN c.tutorialGroups tutorialGroups
                LEFT JOIN tutorialGroups.teachingAssistant tutor
                LEFT JOIN tutorialGroups.registrations registrations
                LEFT JOIN registrations.student student
            WHERE (c.startDate <= :now OR c.startDate IS NULL)
                AND (c.endDate >= :now OR c.endDate IS NULL)
                AND (student.id = :userId OR tutor.id = :userId)
            """)
    Set<Long> findAllActiveTutorialGroupIdsWhereUserIsRegisteredOrTutor(@Param("now") ZonedDateTime now, @Param("userId") Long userId);

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

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
            LEFT JOIN FETCH tutorialGroup.teachingAssistant
            LEFT JOIN FETCH tutorialGroup.course
            WHERE tutorialGroup.id = :#{#tutorialGroupId}
            """)
    Optional<TutorialGroup> findByIdWithTeachingAssistantAndCourse(@Param("tutorialGroupId") long tutorialGroupId);

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
            LEFT JOIN FETCH tutorialGroup.teachingAssistant
            LEFT JOIN FETCH tutorialGroup.registrations
            WHERE tutorialGroup.title = :#{#title}
            AND tutorialGroup.course.id = :#{#courseId}
            """)
    Optional<TutorialGroup> findByTitleAndCourseIdWithTeachingAssistantAndRegistrations(String title, Long courseId);

    Optional<TutorialGroup> findByTutorialGroupChannelId(Long channelId);

    boolean existsByTitleAndCourseId(String title, Long courseId);

    Set<TutorialGroup> findAllByTeachingAssistant(User teachingAssistant);

    @Transactional
    @Modifying
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

    default TutorialGroup findByIdWithTeachingAssistantAndCourseElseThrow(long tutorialGroupId) {
        return this.findByIdWithTeachingAssistantAndCourse(tutorialGroupId).orElseThrow(() -> new EntityNotFoundException("TutorialGroup", tutorialGroupId));
    }

    default TutorialGroup findByIdElseThrow(long tutorialGroupId) {
        return this.findById(tutorialGroupId).orElseThrow(() -> new EntityNotFoundException("TutorialGroup", tutorialGroupId));
    }

    default TutorialGroup findByIdWithTeachingAssistantAndRegistrationsElseThrow(long tutorialGroupId) {
        return this.findByIdWithTeachingAssistantAndRegistrationsAndSessions(tutorialGroupId).orElseThrow(() -> new EntityNotFoundException("TutorialGroup", tutorialGroupId));
    }

    default TutorialGroup findByIdWithTeachingAssistantAndRegistrationsAndSessionsElseThrow(long tutorialGroupId) {
        return this.findByIdWithTeachingAssistantAndRegistrationsAndSessions(tutorialGroupId).orElseThrow(() -> new EntityNotFoundException("TutorialGroup", tutorialGroupId));
    }

    default Optional<Channel> getTutorialGroupChannel(Long tutorialGroupId) {
        return getTutorialGroupWithChannel(tutorialGroupId).map(TutorialGroup::getTutorialGroupChannel);
    }
}
