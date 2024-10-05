package de.tum.cit.aet.artemis.tutorialgroup.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;

@Profile(PROFILE_CORE)
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
