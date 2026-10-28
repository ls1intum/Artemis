package de.tum.cit.aet.artemis.tutorialgroup.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.util.RawTutorialGroupScheduleDTO;

@Conditional(TutorialGroupEnabled.class)
@Lazy
@Repository
public interface TutorialGroupScheduleRepository extends ArtemisJpaRepository<TutorialGroupSchedule, Long> {

    @Query("""
            SELECT tutorialGroupSchedule
            FROM TutorialGroupSchedule tutorialGroupSchedule
                LEFT JOIN FETCH tutorialGroupSchedule.tutorialGroupSessions
            WHERE tutorialGroupSchedule.id = :tutorialGroupScheduleId
            """)
    Optional<TutorialGroupSchedule> findByIdWithSessions(@Param("tutorialGroupScheduleId") long tutorialGroupScheduleId);

    Optional<TutorialGroupSchedule> findByTutorialGroup_Id(Long tutorialGroupId);

    /**
     * Reads the schedule fields of a tutorial group as a scalar projection, ordered by schedule id. A tutorial group maps to a schedule one-to-one, but nothing at the database
     * level enforces this, so a corrupted group may hold several. The projection avoids materializing the schedule entity, whose eager one-to-one back to the group would otherwise
     * fail with a duplicate-row assertion, and the caller keeps only the first row.
     *
     * @param tutorialGroupId the id of the tutorial group
     * @return the schedule rows of the tutorial group, ordered by schedule id
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.tutorialgroup.util.RawTutorialGroupScheduleDTO(
                schedule.dayOfWeek,
                schedule.startTime,
                schedule.endTime,
                schedule.location
            )
            FROM TutorialGroupSchedule schedule
            WHERE schedule.tutorialGroup.id = :tutorialGroupId
            ORDER BY schedule.id ASC
            """)
    List<RawTutorialGroupScheduleDTO> findScheduleData(@Param("tutorialGroupId") long tutorialGroupId);

    Set<TutorialGroupSchedule> getAllByTutorialGroupCourse(Course course);

    default TutorialGroupSchedule findByIdWithSessionsElseThrow(long tutorialGroupScheduleId) {
        return getValueElseThrow(findByIdWithSessions(tutorialGroupScheduleId), tutorialGroupScheduleId);
    }
}
