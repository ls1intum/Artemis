package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
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

    //LeftJoin to exercise categories + + example submission + feedback
    @Query("select te from TextExercise te where te.course.instructorGroupName in :groups and (te.title like %:partialTitle% or te.course.title like %:partialCourseTitle%)")
    Page<TextExercise> findByTitleInExerciseOrCourseAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle,
                                                                             @Param("partialCourseTitle") String partialCourseTitle,
                                                                             @Param("groups") Set<String> groups, Pageable pageable);

    //LeftJoin to exercise categories + + example submission + feedback
    Page<TextExercise> findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContaining(String partialTitle, String partialCourseTitle, Pageable pageable);

    //Page<ProgrammingExercise> findByTitleIgnoreCaseContainingAndShortNameNotNullOrCourse_TitleIgnoreCaseContainingAndShortNameNotNull(String partialTitle, String partialCourseTitle, Pageable pageable);
}
