package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;

/**
 * Spring Data JPA repository for the TextExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TextExerciseRepository extends JpaRepository<TextExercise, Long> {

    @Query("SELECT e FROM TextExercise e WHERE e.course.id = :#{#courseId}")
    List<TextExercise> findByCourseId(@Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "teamAssignmentConfig", "categories" })
    Optional<TextExercise> findWithEagerTeamAssignmentConfigAndCategoriesById(Long exerciseId);

    List<TextExercise> findByAssessmentTypeAndDueDateIsAfter(AssessmentType assessmentType, ZonedDateTime dueDate);

    @Query("select textExercise from TextExercise textExercise where textExercise.course.instructorGroupName in :groups and (textExercise.title like %:partialTitle% or textExercise.course.title like %:partialCourseTitle%)")
    Page<TextExercise> findByTitleInExerciseOrCourseAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle, @Param("partialCourseTitle") String partialCourseTitle,
            @Param("groups") Set<String> groups, Pageable pageable);

    Page<TextExercise> findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContaining(String partialTitle, String partialCourseTitle, Pageable pageable);

    @Query("select textExercise from TextExercise textExercise left join fetch textExercise.exampleSubmissions exampleSubmissions left join fetch exampleSubmissions.submission submission left join fetch submission.result result left join fetch result.feedbacks left join fetch submission.blocks left join fetch result.assessor left join fetch textExercise.teamAssignmentConfig where textExercise.id = :#{#exerciseId}")
    Optional<TextExercise> findByIdWithEagerExampleSubmissionsAndResults(@Param("exerciseId") Long exerciseId);
}
