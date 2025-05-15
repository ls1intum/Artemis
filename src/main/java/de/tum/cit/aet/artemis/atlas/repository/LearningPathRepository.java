package de.tum.cit.aet.artemis.atlas.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;

@Conditional(AtlasEnabled.class)
@Repository
public class LearningPathRepository {

    protected final LearningPathJpaRepository learningPathJpaRepository;

    private final CompetencyRepository competencyRepository;

    private final PrerequisiteRepository prerequisiteRepository;

    public LearningPathRepository(LearningPathJpaRepository learningPathJpaRepository, CompetencyRepository competencyRepository, PrerequisiteRepository prerequisiteRepository) {
        this.learningPathJpaRepository = learningPathJpaRepository;
        this.competencyRepository = competencyRepository;
        this.prerequisiteRepository = prerequisiteRepository;
    }

    public Optional<LearningPath> findByCourseIdAndUserId(long courseId, long userId) {
        return learningPathJpaRepository.findByCourseIdAndUserId(courseId, userId);
    }

    public LearningPath findByCourseIdAndUserIdElseThrow(long courseId, long userId) {
        return learningPathJpaRepository.findByCourseIdAndUserIdElseThrow(courseId, userId);
    }

    public LearningPath findWithEagerUserByIdElseThrow(long learningPathId) {
        return learningPathJpaRepository.findWithEagerUserByIdElseThrow(learningPathId);
    }

    public LearningPath findWithEagerCourseByIdElseThrow(long learningPathId) {
        return learningPathJpaRepository.findWithEagerCourseByIdElseThrow(learningPathId);
    }

    public LearningPath findWithEagerCourseAndCompetenciesByIdElseThrow(long learningPathId) {
        final var learningPath = learningPathJpaRepository.findWithEagerCourseByIdElseThrow(learningPathId);
        return addTransientCompetencies(learningPath);
    }

    public Optional<LearningPath> findWithEagerCompetenciesByCourseIdAndUserId(long courseId, long userId) {
        final var learningPath = learningPathJpaRepository.findByCourseIdAndUserId(courseId, userId);
        return learningPath.map(this::addTransientCompetencies);
    }

    public Page<LearningPath> findByLoginOrNameInCourse(String searchTerm, long courseId, Pageable pageable) {
        return learningPathJpaRepository.findByLoginOrNameInCourse(searchTerm, courseId, pageable);
    }

    public long countLearningPathsOfEnrolledStudentsInCourse(long courseId) {
        return learningPathJpaRepository.countLearningPathsOfEnrolledStudentsInCourse(courseId);
    }

    public Optional<LearningPath> findWithCompetenciesAndLectureUnitsAndExercisesAndLearnerProfileById(long learningPathId) {
        final var learningPath = learningPathJpaRepository.findWithUserAndLearnerProfileById(learningPathId);
        return learningPath.map(this::addTransientCompetenciesAndLectureUnitsAndExercises);
    }

    public LearningPath findWithCompetenciesAndLectureUnitsAndExercisesAndLearnerProfileByIdElseThrow(long learningPathId) {
        return learningPathJpaRepository.getValueElseThrow(findWithCompetenciesAndLectureUnitsAndExercisesAndLearnerProfileById(learningPathId), learningPathId);
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

    public LearningPath save(LearningPath learningPath) {
        return learningPathJpaRepository.save(learningPath);
    }

    public LearningPath findByIdElseThrow(long learningPathId) {
        return learningPathJpaRepository.findByIdElseThrow(learningPathId);
    }
}
