package de.tum.cit.aet.artemis.core.test_repository;

import jakarta.validation.constraints.NotNull;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;

@Repository
public interface CourseTestRepository extends CourseRepository {

    @NotNull
    default Course findWithEagerLearningPathsAndCompetenciesAndPrerequisitesByIdElseThrow(long courseId) {
        return getValueElseThrow(findWithEagerLearningPathsAndCompetenciesAndPrerequisitesById(courseId), courseId);
    }
}
