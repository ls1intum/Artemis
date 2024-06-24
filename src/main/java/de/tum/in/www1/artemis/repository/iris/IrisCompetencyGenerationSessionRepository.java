package de.tum.in.www1.artemis.repository.iris;

import de.tum.in.www1.artemis.domain.iris.session.IrisCompetencyGenerationSession;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

/**
 * Repository interface for managing {@link IrisCompetencyGenerationSession} entities.
 */
public interface IrisCompetencyGenerationSessionRepository extends ArtemisJpaRepository<IrisCompetencyGenerationSession, Long> {

    /**
     * Finds the latest {@link IrisCompetencyGenerationSession} based on its course and user.
     *
     * @param courseId The ID of the course.
     * @param userId   The ID of the user.
     *
     * @return The latest competency generation session
     */
    IrisCompetencyGenerationSession findFirstByCourseIdAndUserIdOrderByCreationDateDesc(long courseId, long userId);
}
