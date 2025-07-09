package de.tum.cit.aet.artemis.atlas.service.learningpath;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearningPathRepository;
import de.tum.cit.aet.artemis.atlas.repository.PrerequisiteRepository;

/**
 * Service to fetch learning paths with transient competencies.
 */
@Conditional(AtlasEnabled.class)
@Service
@Lazy
public class LearningPathRepositoryService {

    private final LearningPathRepository learningPathRepository;

    private final CompetencyRepository competencyRepository;

    private final PrerequisiteRepository prerequisiteRepository;

    public LearningPathRepositoryService(LearningPathRepository learningPathRepository, CompetencyRepository competencyRepository, PrerequisiteRepository prerequisiteRepository) {
        this.learningPathRepository = learningPathRepository;
        this.competencyRepository = competencyRepository;
        this.prerequisiteRepository = prerequisiteRepository;
    }

    private LearningPath addTransientCompetencies(LearningPath learningPath) {
        final var competencies = competencyRepository.findByLearningPathId(learningPath.getId());
        final var prerequisites = prerequisiteRepository.findByLearningPathId(learningPath.getId());
        return addTransientCompetencies(learningPath, competencies, prerequisites);
    }

    private LearningPath addTransientCompetenciesAndLectureUnitsAndExercises(LearningPath learningPath) {
        final var competencies = competencyRepository.findByLearningPathIdWithLectureUnitsAndExercises(learningPath.getId());
        final var prerequisites = prerequisiteRepository.findByLearningPathIdWithLectureUnitsAndExercises(learningPath.getId());
        return addTransientCompetencies(learningPath, competencies, prerequisites);
    }

    public LearningPath addTransientCompetencies(LearningPath learningPath, Set<Competency> competencies, Set<Prerequisite> prerequisites) {
        learningPath.addCompetencies(competencies);
        learningPath.addCompetencies(prerequisites);
        return learningPath;
    }

    public LearningPath findWithEagerUserAndCourseAndCompetenciesByIdElseThrow(long learningPathId) {
        final var learningPath = learningPathRepository.findWithEagerUserAndCourseByIdElseThrow(learningPathId);
        return addTransientCompetencies(learningPath);
    }

    public Optional<LearningPath> findWithEagerCompetenciesByCourseIdAndUserId(long courseId, long userId) {
        final var learningPath = learningPathRepository.findByCourseIdAndUserId(courseId, userId);
        return learningPath.map(this::addTransientCompetencies);
    }

    public Optional<LearningPath> findWithCompetenciesAndLectureUnitsAndExercisesAndLearnerProfileById(long learningPathId) {
        final var learningPath = learningPathRepository.findWithEagerUserAndLearnerProfileById(learningPathId);
        return learningPath.map(this::addTransientCompetenciesAndLectureUnitsAndExercises);
    }

    public LearningPath findWithCompetenciesAndLectureUnitsAndExercisesAndLearnerProfileByIdElseThrow(long learningPathId) {
        return learningPathRepository.getValueElseThrow(findWithCompetenciesAndLectureUnitsAndExercisesAndLearnerProfileById(learningPathId), learningPathId);
    }

    public LearningPath findWithEagerCompetenciesByCourseIdAndUserIdElseThrow(long courseId, long userId) {
        return learningPathRepository.getValueElseThrow(findWithEagerCompetenciesByCourseIdAndUserId(courseId, userId));
    }
}
