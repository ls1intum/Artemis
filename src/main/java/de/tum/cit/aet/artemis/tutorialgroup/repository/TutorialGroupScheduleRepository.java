package de.tum.cit.aet.artemis.tutorialgroup.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;

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

    Set<TutorialGroupSchedule> getAllByTutorialGroupCourse(Course course);

    default TutorialGroupSchedule findByIdWithSessionsElseThrow(long tutorialGroupScheduleId) {
        return getValueElseThrow(findByIdWithSessions(tutorialGroupScheduleId), tutorialGroupScheduleId);
    }
}
