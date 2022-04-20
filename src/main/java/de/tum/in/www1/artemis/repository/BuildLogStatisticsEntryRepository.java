package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.statistics.BuildLogStatisticsEntry;

/**
 * Spring Data JPA repository for the BuildLogStatisticsEntry entity.
 */
@Repository
public interface BuildLogStatisticsEntryRepository extends JpaRepository<BuildLogStatisticsEntry, Long> {

    @Query("""
                select avg(b.dockerSetupDuration), avg(b.testDuration), avg(b.scaDuration), avg(b.totalJobDuration), avg(b.dependenciesDownloadedCount)
                from BuildLogStatisticsEntry b, Submission s, Participation p
                where b.programmingSubmission = s
                and s.participation = p
                and p.exercise = :#{#exercise}
            """)
    public Object findAverageBuildLogStatisticsEntryForExercise(@Param("exercise") ProgrammingExercise exercise);

}
