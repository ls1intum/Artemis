package de.tum.cit.aet.artemis.core.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;

@Lazy
@Repository
@Primary
public interface CourseTestRepository extends CourseRepository {

    @Transactional
    @Modifying
    @Query("UPDATE Course c SET c.semester = NULL WHERE c.semester IS NOT NULL")
    void clearSemester();

    @EntityGraph(type = LOAD, attributePaths = { "learningPaths" })
    Optional<Course> findWithEagerLearningPathsById(@Param("courseId") long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "exercises", "lectures", "lectures.lectureUnits", "lectures.attachments", "competencies", "prerequisites" })
    Optional<Course> findWithEagerExercisesAndLecturesAndLectureUnitsAndCompetenciesById(long courseId);

    @NotNull
    default Course findWithEagerLearningPathsByIdElseThrow(long courseId) {
        return getValueElseThrow(findWithEagerLearningPathsById(courseId), courseId);
    }

    @EntityGraph(type = LOAD, attributePaths = { "competencies", "prerequisites", "learningPaths" })
    Optional<Course> findWithEagerCompetenciesAndPrerequisitesAndLearningPathsById(@Param("courseId") long courseId);

    @NotNull
    default Course findWithEagerCompetenciesAndPrerequisitesAndLearningPathsByIdElseThrow(long courseId) {
        return getValueElseThrow(findWithEagerCompetenciesAndPrerequisitesAndLearningPathsById(courseId), courseId);
    }

    @NotNull
    default Course findByIdWithExercisesAndLecturesAndLectureUnitsAndCompetenciesElseThrow(long courseId) {
        return getValueElseThrow(findWithEagerExercisesAndLecturesAndLectureUnitsAndCompetenciesById(courseId), courseId);
    }
}
