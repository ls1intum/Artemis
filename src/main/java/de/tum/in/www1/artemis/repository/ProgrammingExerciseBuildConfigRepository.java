package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.hibernate.Hibernate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseBuildConfig;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface ProgrammingExerciseBuildConfigRepository extends ArtemisJpaRepository<ProgrammingExerciseBuildConfig, Long> {

    Optional<ProgrammingExerciseBuildConfig> findByProgrammingExerciseId(Long programmingExerciseId);

    default ProgrammingExerciseBuildConfig getProgrammingExerciseWithBuildConfigElseThrow(ProgrammingExercise programmingExercise) {
        if (programmingExercise.getBuildConfig() == null || !Hibernate.isInitialized(programmingExercise.getBuildConfig())) {
            return findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        }
        return programmingExercise.getBuildConfig();
    }
}
