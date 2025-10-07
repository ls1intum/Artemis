package de.tum.cit.aet.artemis.versioning.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Repository for TextExercise, with minimal data necessary for exercise versioning.
 * This repository is created due to TextExerciseRepository being restricted by TextApiArchitectureTest to be only used with in ".text." package.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface TextExerciseVersioningRepository extends ArtemisJpaRepository<TextExercise, Long> {

    /**
     * Finds a TextExercise with minimal data necessary for exercise versioning.
     * Only includes core configuration data, NOT submissions, results, or example submissions.
     * Basic TextExercise fields (exampleSolution) are already included in the entity.
     *
     * @param exerciseId the id of the exercise to fetch
     * @return {@link TextExercise}
     */
    @EntityGraph(type = LOAD, attributePaths = { "competencyLinks", "categories", "teamAssignmentConfig", "gradingCriteria", "plagiarismDetectionConfig" })
    Optional<TextExercise> findForVersioningById(long exerciseId);
}
