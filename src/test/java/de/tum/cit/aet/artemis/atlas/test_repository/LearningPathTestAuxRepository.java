package de.tum.cit.aet.artemis.atlas.test_repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.repository.LearningPathJpaRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearningPathRepository;

/**
 * This is an auxiliary repository for testing purposes.
 * It replaces our usual approach of moving queries that are only needed for testing purposes to a test repository. In this case this is not possible, due to different handling of
 * Class vs. Interface repositories by Spring.
 */
@Repository
public class LearningPathTestAuxRepository {

    @Autowired
    private LearningPathJpaRepository learningPathJpaRepository;

    @Autowired
    private LearningPathRepository learningPathRepository;

    public LearningPath findWithEagerCompetenciesByCourseIdAndUserIdElseThrow(long courseId, long userId) {
        return learningPathJpaRepository.getValueElseThrow(learningPathRepository.findWithEagerCompetenciesByCourseIdAndUserId(courseId, userId));
    }

    public void deleteAll(Iterable<LearningPath> learningPaths) {
        learningPathJpaRepository.deleteAll(learningPaths);
    }
}
