package de.tum.cit.aet.artemis.core.test_repository;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.repository.LearningPathRepository;

@Repository
public interface LearningPathTestRepository extends LearningPathRepository {

    default LearningPath findWithEagerCompetenciesByCourseIdAndUserIdElseThrow(long courseId, long userId) {
        return getValueElseThrow(findWithEagerCompetenciesByCourseIdAndUserId(courseId, userId));
    }
}
