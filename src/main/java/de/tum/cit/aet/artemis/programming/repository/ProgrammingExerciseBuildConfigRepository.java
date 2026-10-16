package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseBuildPlanConfigurationDTO;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ProgrammingExerciseBuildConfigRepository extends ArtemisJpaRepository<ProgrammingExerciseBuildConfig, Long> {

    Optional<ProgrammingExerciseBuildConfig> findByProgrammingExerciseId(Long programmingExerciseId);

    /**
     * Reads the build configurations of every exercise that belongs to a project.
     *
     * @param projectKey the project key shared by the exercises
     * @return one build configuration per exercise in that project
     */
    @Query("""
            SELECT buildConfig
            FROM ProgrammingExerciseBuildConfig buildConfig
            WHERE buildConfig.programmingExercise.projectKey = :projectKey
            """)
    List<ProgrammingExerciseBuildConfig> findAllByProjectKey(@Param("projectKey") String projectKey);

    /**
     * Reads the build plan configuration of several exercises at once.
     * <p>
     * For callers that need the build plan of every programming exercise in an exam: one query for all of them,
     * projecting only the two values they read rather than the whole configuration row.
     *
     * @param exerciseIds the exercises whose build plan configuration to read
     * @return one entry per exercise that has a configuration, in no particular order
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseBuildPlanConfigurationDTO(
                buildConfig.id,
                buildConfig.programmingExercise.id,
                buildConfig.buildPlanConfiguration)
            FROM ProgrammingExerciseBuildConfig buildConfig
            WHERE buildConfig.programmingExercise.id IN :exerciseIds
            """)
    List<ProgrammingExerciseBuildPlanConfigurationDTO> findBuildPlanConfigurationsByProgrammingExerciseIds(@Param("exerciseIds") Collection<Long> exerciseIds);

    /**
     * Reads the build configuration of an exercise.
     *
     * @param programmingExerciseId the id of the exercise whose configuration to read
     * @return the build configuration
     */
    default ProgrammingExerciseBuildConfig getProgrammingExerciseBuildConfigElseThrow(long programmingExerciseId) {
        return getValueElseThrow(findByProgrammingExerciseId(programmingExerciseId));
    }

    /**
     * Writes a build configuration for an exercise that already exists, since the configuration carries the key.
     *
     * @param buildConfig         the configuration to write
     * @param programmingExercise the exercise it belongs to
     * @return the stored build configuration
     */
    default ProgrammingExerciseBuildConfig saveForExercise(ProgrammingExerciseBuildConfig buildConfig, ProgrammingExercise programmingExercise) {
        buildConfig.setProgrammingExercise(programmingExercise);
        return save(buildConfig);
    }

    default void generateBuildPlanAccessSecretIfNotExists(ProgrammingExerciseBuildConfig buildConfig) {
        if (!buildConfig.hasBuildPlanAccessSecretSet()) {
            buildConfig.generateAndSetBuildPlanAccessSecret();
            save(buildConfig);
        }
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
