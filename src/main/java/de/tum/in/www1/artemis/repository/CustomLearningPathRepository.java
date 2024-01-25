package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public interface CustomLearningPathRepository {

    /**
     * Gets a learning path with eagerly fetched competencies, linked lecture units and exercises, and the corresponding domain objects storing the progress.
     * <p>
     * The query only fetches data related to the owner of the learning path. participations and progress for other users are not included.
     * <p>
     * JPA neither supports JOIN-FETCH-ON statements nor entity graphs combined with queries. Hence, we have to use a custom implementation.
     * This custom implementation overrides all default eager loading strategies. So if a dependency is missing, add it to the entity graph on {@link LearningPath}
     *
     * @param learningPathId the id of the learning path to fetch
     * @return the learning path with fetched data
     */
    Optional<LearningPath> findWithEagerCompetenciesAndProgressAndLearningObjectsAndCompletedUsersById(@Param("learningPathId") long learningPathId);

    default LearningPath findWithEagerCompetenciesAndProgressAndLearningObjectsAndCompletedUsersByIdElseThrow(long learningPathId) {
        return findWithEagerCompetenciesAndProgressAndLearningObjectsAndCompletedUsersById(learningPathId)
                .orElseThrow(() -> new EntityNotFoundException("LearningPath", learningPathId));
    }
}
