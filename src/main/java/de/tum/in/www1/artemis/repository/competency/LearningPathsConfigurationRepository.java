package de.tum.in.www1.artemis.repository.competency;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.competency.LearningPathsConfiguration;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

public interface LearningPathsConfigurationRepository extends ArtemisJpaRepository<LearningPathsConfiguration, Long> {

    @Query("""
                SELECT lpc
                FROM LearningPathsConfiguration lpc
                    LEFT JOIN lpc.course c
                    LEFT JOIN c.learningPaths l
                WHERE l.id = :learningPathId
            """)
    Optional<LearningPathsConfiguration> findByLearningPathId(@Param("learningPathId") long learningPathId);

    default LearningPathsConfiguration findByLearningPathIdElseThrow(long learningPathId) {
        return getValueElseThrow(findByLearningPathId(learningPathId), learningPathId);
    }
}
