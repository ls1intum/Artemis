package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseAthenaConfig;

/**
 * Spring Data JPA repository for the ExerciseAthenaConfig entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ExerciseAthenaConfigRepository extends ArtemisJpaRepository<ExerciseAthenaConfig, Long> {

    /**
     * Find the Athena configuration for a given exercise.
     *
     * @param exerciseId the ID of the exercise
     * @return the Athena configuration for the exercise, or empty if not found
     */
    Optional<ExerciseAthenaConfig> findByExerciseId(Long exerciseId);

    /**
     * Delete the Athena configuration for a given exercise.
     *
     * @param exerciseId the ID of the exercise
     */
    @Modifying(clearAutomatically = true)
    @Transactional // ok because of delete
    void deleteByExerciseId(Long exerciseId);

    /**
     * Revokes access to restricted Athena modules by nulling out the preliminary and graded
     * feedback module fields for all exercises in the given course that reference a restricted module.
     *
     * @param courseId          the ID of the course
     * @param restrictedModules the list of restricted module names
     */
    @Modifying
    @Transactional // ok because of bulk update
    @Query("""
            UPDATE ExerciseAthenaConfig c
            SET c.preliminaryFeedbackModule = CASE WHEN c.preliminaryFeedbackModule IN :restrictedModules THEN NULL ELSE c.preliminaryFeedbackModule END,
                c.gradedFeedbackModule = CASE WHEN c.gradedFeedbackModule IN :restrictedModules THEN NULL ELSE c.gradedFeedbackModule END
            WHERE c.exercise.course.id = :courseId
                  AND (c.preliminaryFeedbackModule IN :restrictedModules OR c.gradedFeedbackModule IN :restrictedModules)
            """)
    void revokeRestrictedModulesByCourseId(@Param("courseId") Long courseId, @Param("restrictedModules") Collection<String> restrictedModules);
}
