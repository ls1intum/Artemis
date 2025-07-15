package de.tum.cit.aet.artemis.tutorialgroup.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;

@ConditionalOnProperty(name = "artemis.tutorialgroup.enabled", havingValue = "true")
@Repository
public interface TutorialGroupScheduleRepository extends ArtemisJpaRepository<TutorialGroupSchedule, Long> {

    @Query("""
            SELECT tutorialGroupSchedule
            FROM TutorialGroupSchedule tutorialGroupSchedule
                LEFT JOIN FETCH tutorialGroupSchedule.tutorialGroupSessions
            WHERE tutorialGroupSchedule.id = :tutorialGroupScheduleId
            """)
    Optional<TutorialGroupSchedule> findByIdWithSessions(@Param("tutorialGroupScheduleId") long tutorialGroupScheduleId);

    Set<TutorialGroupSchedule> getAllByTutorialGroupCourse(Course course);

    default TutorialGroupSchedule findByIdWithSessionsElseThrow(long tutorialGroupScheduleId) {
        return getValueElseThrow(findByIdWithSessions(tutorialGroupScheduleId), tutorialGroupScheduleId);
    }
}
