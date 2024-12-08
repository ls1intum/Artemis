package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildStatistics;

@Profile(PROFILE_CORE)
@Repository
public interface ProgrammingExerciseBuildStatisticsRepository extends ArtemisJpaRepository<ProgrammingExerciseBuildStatistics, Long> {

    Optional<ProgrammingExerciseBuildStatistics> findByExerciseId(Long exerciseId);
}
