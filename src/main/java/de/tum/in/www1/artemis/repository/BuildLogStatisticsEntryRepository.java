package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.statistics.BuildLogStatisticsEntry;
import de.tum.in.www1.artemis.web.rest.dto.BuildLogStatisticsDTO;

/**
 * Spring Data JPA repository for the BuildLogStatisticsEntry entity.
 */
@Repository
public interface BuildLogStatisticsEntryRepository extends JpaRepository<BuildLogStatisticsEntry, Long> {

    @Query("""
                SELECT new de.tum.in.www1.artemis.web.rest.dto.BuildLogStatisticsDTO(count(b.id), avg(b.agentSetupDuration), avg(b.testDuration), avg(b.scaDuration), avg(b.totalJobDuration), avg(b.dependenciesDownloadedCount))
                FROM BuildLogStatisticsEntry b, Submission s, Participation p
                WHERE b.programmingSubmission = s
                    AND s.participation = p
                    AND (
                        p.exercise = :#{#exercise}
                        OR :#{#exercise.solutionParticipation != null ? #exercise.solutionParticipation.id : #exercise.solutionParticipation} = p.id
                        OR :#{#exercise.templateParticipation != null ? #exercise.templateParticipation.id : #exercise.templateParticipation} = p.id
                    )
            """)
    BuildLogStatisticsDTO findAverageBuildLogStatisticsEntryForExercise(@Param("exercise") ProgrammingExercise exercise);

}
