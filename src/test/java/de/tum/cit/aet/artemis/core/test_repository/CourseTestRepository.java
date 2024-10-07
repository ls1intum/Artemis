package de.tum.cit.aet.artemis.core.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;

@Repository
@Primary
public interface CourseTestRepository extends CourseRepository {

    @EntityGraph(type = LOAD, attributePaths = { "competencies", "prerequisites", "learningPaths", "learningPaths.competencies" })
    Optional<Course> findWithEagerLearningPathsAndCompetenciesAndPrerequisitesById(long courseId);

    @NotNull
    default Course findWithEagerLearningPathsAndCompetenciesAndPrerequisitesByIdElseThrow(long courseId) {
        return getValueElseThrow(findWithEagerLearningPathsAndCompetenciesAndPrerequisitesById(courseId), courseId);
    }
}
