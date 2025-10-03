package de.tum.cit.aet.artemis.tutorialgroup.repository;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.util.RawTutorialGroupDetailGroupDTO;

@Conditional(TutorialGroupEnabled.class)
@Lazy
@Repository
public interface TutorialGroupRepository extends ArtemisJpaRepository<TutorialGroup, Long> {

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
                LEFT JOIN FETCH tutorialGroup.tutorialGroupChannel
            WHERE tutorialGroup.id = :tutorialGroupId
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
            WHERE tutorialGroup.course.instructorGroupName IN :userGroups
                AND tutorialGroup.campus IS NOT NULL
            """)
    Set<String> findAllUniqueCampusValuesInRegisteredCourse(@Param("userGroups") Set<String> userGroups);

    @Query("""
            SELECT DISTINCT tutorialGroup.language
            FROM TutorialGroup tutorialGroup
            WHERE tutorialGroup.course.instructorGroupName IN :userGroups
                AND tutorialGroup.language IS NOT NULL
            """)
    Set<String> findAllUniqueLanguageValuesInRegisteredCourse(@Param("userGroups") Set<String> userGroups);

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
            WHERE tutorialGroup.course.id = :courseId
            ORDER BY tutorialGroup.title
            """)
    Set<TutorialGroup> findAllByCourseId(@Param("courseId") Long courseId);

    @Query("""
            SELECT DISTINCT tutorialGroup.id
            FROM TutorialGroup tutorialGroup
                JOIN tutorialGroup.course course
                LEFT JOIN tutorialGroup.registrations registration
                LEFT JOIN registration.student student
                LEFT JOIN tutorialGroup.teachingAssistant teachingAssistant
            WHERE (student.id = :userId OR teachingAssistant.id = :userId) AND (course.id = :courseId)
            """)
    Set<Long> findTutorialGroupIdsWhereUserParticipatesForCourseId(@Param("courseId") long courseId, @Param("userId") long userId);

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
                LEFT JOIN FETCH tutorialGroup.tutorialGroupChannel
            WHERE tutorialGroup.course.id = :courseId
            ORDER BY tutorialGroup.title
            """)
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
                LEFT JOIN FETCH tutorialGroup.tutorialGroupSessions
                LEFT JOIN FETCH tutorialGroup.tutorialGroupSchedule
                LEFT JOIN FETCH tutorialGroup.tutorialGroupChannel
            WHERE tutorialGroup.course.id = :courseId
            ORDER BY tutorialGroup.title
            """)
    Set<TutorialGroup> findAllByCourseIdWithTeachingAssistantRegistrationsAndSchedule(@Param("courseId") Long courseId);

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
                LEFT JOIN FETCH tutorialGroup.teachingAssistant
                LEFT JOIN FETCH tutorialGroup.registrations
                LEFT JOIN FETCH tutorialGroup.tutorialGroupSessions
                LEFT JOIN FETCH tutorialGroup.tutorialGroupSchedule
                LEFT JOIN FETCH tutorialGroup.tutorialGroupChannel
            WHERE tutorialGroup.id = :tutorialGroupId
            """)
    Optional<TutorialGroup> findByIdWithTeachingAssistantAndRegistrationsAndSessions(@Param("tutorialGroupId") long tutorialGroupId);

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
                LEFT JOIN FETCH tutorialGroup.teachingAssistant
                LEFT JOIN FETCH tutorialGroup.course
            WHERE tutorialGroup.id = :tutorialGroupId
            """)
    Optional<TutorialGroup> findByIdWithTeachingAssistantAndCourse(@Param("tutorialGroupId") long tutorialGroupId);

    Optional<TutorialGroup> findByTutorialGroupChannelId(Long channelId);

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
                LEFT JOIN FETCH tutorialGroup.tutorialGroupSessions
            WHERE tutorialGroup.id = :tutorialGroupId
            """)
    Optional<TutorialGroup> findByIdWithSessions(@Param("tutorialGroupId") long tutorialGroupId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.tutorialgroup.util.RawTutorialGroupDetailGroupDTO(
                tutorialGroup.id,
                tutorialGroup.title,
                tutorialGroup.language,
                tutorialGroup.isOnline,
                tutorialGroup.capacity,
                tutorialGroup.campus,
                CONCAT(tutorialGroup.teachingAssistant.firstName, CONCAT(' ', tutorialGroup.teachingAssistant.lastName)),
                tutorialGroup.teachingAssistant.login,
                tutorialGroup.teachingAssistant.imageUrl,
                channel.id,
                schedule.dayOfWeek,
                schedule.startTime,
                schedule.endTime,
                schedule.location
            )
            FROM TutorialGroup tutorialGroup
                LEFT JOIN tutorialGroup.tutorialGroupChannel channel
                LEFT JOIN tutorialGroup.tutorialGroupSchedule schedule
            WHERE tutorialGroup.id = :tutorialGroupId AND tutorialGroup.course.id = :courseId
            """)
    Optional<RawTutorialGroupDetailGroupDTO> getTutorialGroupDetailData(@Param("tutorialGroupId") long tutorialGroupId, @Param("courseId") long courseId);

    default TutorialGroup findByIdWithSessionsElseThrow(long tutorialGroupId) {
        return getValueElseThrow(findByIdWithSessions(tutorialGroupId), tutorialGroupId);
    }

    default TutorialGroup findByIdWithTeachingAssistantAndCourseElseThrow(long tutorialGroupId) {
        return getValueElseThrow(findByIdWithTeachingAssistantAndCourse(tutorialGroupId), tutorialGroupId);
    }

    default TutorialGroup findByIdWithTeachingAssistantAndRegistrationsElseThrow(long tutorialGroupId) {
        return getValueElseThrow(findByIdWithTeachingAssistantAndRegistrationsAndSessions(tutorialGroupId), tutorialGroupId);
    }

    default TutorialGroup findByIdWithTeachingAssistantAndRegistrationsAndSessionsElseThrow(long tutorialGroupId) {
        return getValueElseThrow(findByIdWithTeachingAssistantAndRegistrationsAndSessions(tutorialGroupId), tutorialGroupId);
    }

    default Optional<Channel> getTutorialGroupChannel(Long tutorialGroupId) {
        return getTutorialGroupWithChannel(tutorialGroupId).map(TutorialGroup::getTutorialGroupChannel);
    }

    Long countByCourse(Course course);
}
