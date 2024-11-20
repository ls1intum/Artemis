package de.tum.cit.aet.artemis.programming.repository;

import java.util.Optional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildStatistics;

public interface ProgrammingExerciseBuildStatisticsRepository extends ArtemisJpaRepository<ProgrammingExerciseBuildStatistics, Long> {

    Optional<ProgrammingExerciseBuildStatistics> findByExerciseId(Long exerciseId);
}
