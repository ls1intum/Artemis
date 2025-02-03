package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.hibernate.Hibernate;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;

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

    /**
     * Gets the theiaImage by its programming exercise's id
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The theiaImage of the programming exercise's build config
     */
    @Query("""
            SELECT pebc.theiaImage
            FROM ProgrammingExerciseBuildConfig pebc
            WHERE pebc.programmingExercise.id = :programmingExerciseId
            """)
    String getTheiaImageByProgrammingExerciseId(@Param("programmingExerciseId") long programmingExerciseId);
}
