package de.tum.in.www1.artemis.repository.iris;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.iris.session.IrisCompetencyGenerationSession;

public interface IrisCompetencyGenerationSessionRepository extends JpaRepository<IrisCompetencyGenerationSession, Long> {

    /**
     * Finds a list of {@link IrisCompetencyGenerationSession} based on its course and user.
     *
     * @param courseId The ID of the course.
     * @param userId   The ID of the user.
     *
     * @return A list of competency generation sessions sorted by creation date in descending order.
     */
    List<IrisCompetencyGenerationSession> findByCourseIdAndUserIdOrderByCreationDateDesc(long courseId, long userId);
}
