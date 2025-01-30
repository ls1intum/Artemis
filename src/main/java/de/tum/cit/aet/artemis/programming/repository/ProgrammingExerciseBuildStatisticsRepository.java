package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildStatistics;

@Profile(PROFILE_CORE)
@Repository
public interface ProgrammingExerciseBuildStatisticsRepository extends ArtemisJpaRepository<ProgrammingExerciseBuildStatistics, Long> {

    Optional<ProgrammingExerciseBuildStatistics> findByExerciseId(Long exerciseId);

    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ProgrammingExerciseBuildStatistics s
            SET s.buildDurationSeconds = :averageDuration, s.buildCountWhenUpdated = :buildCount
            WHERE s.exerciseId = :exerciseId
            """)
    void updateStatistics(@Param("averageDuration") long averageDuration, @Param("buildCount") long buildCount, @Param("exerciseId") Long exerciseId);
}
