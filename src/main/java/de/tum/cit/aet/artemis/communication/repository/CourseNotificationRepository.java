package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.CourseNotification;
import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationWithStatusDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Repository for the {@link CourseNotification} entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CourseNotificationRepository extends ArtemisJpaRepository<CourseNotification, Long> {

    /**
     * Find all course notifications for a specific user and course where the status is not archived (value 2)
     * according to {{@link UserCourseNotificationStatusType}}.
     *
     * @param userId   the ID of the user
     * @param courseId the ID of the course
     * @param pageable pageable to filter for
     * @return list of course notifications that match the criteria
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.communication.dto.CourseNotificationWithStatusDTO(cn, us)
            FROM CourseNotification cn
                JOIN cn.userStatuses us
            WHERE us.user.id = :userId
                AND cn.course.id = :courseId
                AND us.status <> 2
            ORDER BY cn.id DESC
            """)
    Page<CourseNotificationWithStatusDTO> findCourseNotificationsByUserIdAndCourseIdAndStatusNotArchived(@Param("userId") Long userId, @Param("courseId") Long courseId,
            Pageable pageable);

    /**
     * Find all course notifications that are past the specified deletion date.
     *
     * @param date deletion date to query for
     * @return list of course notifications that should be deleted
     */
    List<CourseNotification> findByDeletionDateBefore(ZonedDateTime date);

    @Transactional // ok because of delete
    @Modifying
    List<CourseNotification> deleteAllByCourseId(long courseId);
}
