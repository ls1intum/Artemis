package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.hibernate.Hibernate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface ProgrammingExerciseBuildConfigRepository extends ArtemisJpaRepository<ProgrammingExerciseBuildConfig, Long> {

    Optional<ProgrammingExerciseBuildConfig> findByProgrammingExerciseId(Long programmingExerciseId);

    default ProgrammingExerciseBuildConfig getProgrammingExerciseBuildConfigElseThrow(ProgrammingExercise programmingExercise) {
        if (programmingExercise.getBuildConfig() == null || !Hibernate.isInitialized(programmingExercise.getBuildConfig())) {
            return getValueElseThrow(findByProgrammingExerciseId(programmingExercise.getId()));
        }
        return programmingExercise.getBuildConfig();
    }

    default void generateBuildPlanAccessSecretIfNotExists(ProgrammingExerciseBuildConfig buildConfig) {
        if (!buildConfig.hasBuildPlanAccessSecretSet()) {
            buildConfig.generateAndSetBuildPlanAccessSecret();
            save(buildConfig);
        }
    }

    default void loadAndSetBuildConfig(ProgrammingExercise programmingExercise) {
        programmingExercise.setBuildConfig(getProgrammingExerciseBuildConfigElseThrow(programmingExercise));
    }
}
