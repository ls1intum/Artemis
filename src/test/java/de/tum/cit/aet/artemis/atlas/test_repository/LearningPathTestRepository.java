package de.tum.cit.aet.artemis.atlas.test_repository;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearningPathJpaRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearningPathRepository;
import de.tum.cit.aet.artemis.atlas.repository.PrerequisiteRepository;

@Repository
@Primary
public class LearningPathTestRepository extends LearningPathRepository {

    public LearningPathTestRepository(LearningPathJpaRepository learningPathJpaRepository, CompetencyRepository competencyRepository,
            PrerequisiteRepository prerequisiteRepository) {
        super(learningPathJpaRepository, competencyRepository, prerequisiteRepository);
    }

    public LearningPath findWithEagerCompetenciesByCourseIdAndUserIdElseThrow(long courseId, long userId) {
        return learningPathJpaRepository.getValueElseThrow(findWithEagerCompetenciesByCourseIdAndUserId(courseId, userId));
    }

    public void deleteAll(Iterable<LearningPath> learningPaths) {
        learningPathJpaRepository.deleteAll(learningPaths);
    }
}
