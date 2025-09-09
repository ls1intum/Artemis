package de.tum.cit.aet.artemis.versioning.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;

/**
 * Repository for FileUploadExercise, with minimal data necessary for exercise versioning.
 * This repository is created due to FileUploadExerciseRepository being restricted by FileUploadApiArchitectureTest to be only used with in ".fileupload." package.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface FileUploadExerciseVersioningRepository extends ArtemisJpaRepository<FileUploadExercise, Long> {

    /**
     * Finds a FileUploadExercise with minimal data necessary for exercise versioning.
     * Only includes core configuration data, NOT submissions, results, or example submissions.
     * Basic FileUploadExercise fields (exampleSolution, filePattern) are already included in the entity.
     */
    @EntityGraph(type = LOAD, attributePaths = { "competencyLinks", "competencyLinks.competency", "categories", "teamAssignmentConfig", "gradingCriteria",
            "exampleSubmissions.submission", "attachments", "plagiarismDetectionConfig" })
    Optional<FileUploadExercise> findWithEagerForVersioningById(long exerciseId);

}
