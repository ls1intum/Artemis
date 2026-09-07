package de.tum.cit.aet.artemis.core.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;

@Lazy
@Repository
@Primary
public interface CourseTestRepository extends CourseRepository {

    @EntityGraph(type = LOAD, attributePaths = { "learningPaths" })
    Optional<Course> findWithEagerLearningPathsById(@Param("courseId") long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "exercises", "lectures", "lectures.lectureUnits", "competencies", "prerequisites" })
    Optional<Course> findWithEagerExercisesAndLecturesAndLectureUnitsAndCompetenciesById(long courseId);

    @NonNull
    default Course findWithEagerLearningPathsByIdElseThrow(long courseId) {
        return getValueElseThrow(findWithEagerLearningPathsById(courseId), courseId);
    }

    @EntityGraph(type = LOAD, attributePaths = { "competencies", "prerequisites", "learningPaths" })
    Optional<Course> findWithEagerCompetenciesAndPrerequisitesAndLearningPathsById(@Param("courseId") long courseId);

    @NonNull
    default Course findWithEagerCompetenciesAndPrerequisitesAndLearningPathsByIdElseThrow(long courseId) {
        return getValueElseThrow(findWithEagerCompetenciesAndPrerequisitesAndLearningPathsById(courseId), courseId);
    }

    @NonNull
    default Course findByIdWithExercisesAndLecturesAndLectureUnitsAndCompetenciesElseThrow(long courseId) {
        return getValueElseThrow(findWithEagerExercisesAndLecturesAndLectureUnitsAndCompetenciesById(courseId), courseId);
    }

    @EntityGraph(type = LOAD, attributePaths = { "lectures", "lectures.lectureUnits" })
    Optional<Course> findWithLecturesAndLectureUnitsById(long courseId);

    @NonNull
    default Course findWithLecturesAndLectureUnitsByIdElseThrow(long courseId) {
        return getValueElseThrow(findWithLecturesAndLectureUnitsById(courseId), courseId);
    }
}
