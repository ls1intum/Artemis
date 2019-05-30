package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextSubmission;

/**
 * Spring Data JPA repository for the TextSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TextSubmissionRepository extends JpaRepository<TextSubmission, Long> {

    List<TextSubmission> findByIdIn(List<Long> textSubmissionsId);

    @Query("select distinct submission from TextSubmission submission left join fetch submission.result r left join fetch r.assessor where submission.id = :#{#submissionId}")
    Optional<TextSubmission> findByIdWithEagerResultAndAssessor(@Param("submissionId") Long submissionId);

    /**
     * @param courseId the course id we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true and the submission date before the exercise due date
     */
    @Query("SELECT COUNT (DISTINCT textSubmission) FROM TextSubmission textSubmission WHERE textSubmission.participation.exercise.course.id = :#{#courseId} AND textSubmission.submitted = TRUE AND textSubmission.submissionDate < textSubmission.participation.exercise.dueDate")
    long countByCourseIdSubmittedBeforeDueDate(@Param("courseId") Long courseId);

    /**
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true and the submission date before the exercise due date
     */
    @Query("SELECT COUNT (DISTINCT textSubmission) FROM TextSubmission textSubmission WHERE textSubmission.participation.exercise.id = :#{#exerciseId} AND textSubmission.submitted = TRUE AND textSubmission.submissionDate < textSubmission.participation.exercise.dueDate")
    long countByExerciseIdSubmittedBeforeDueDate(@Param("exerciseId") Long exerciseId);
}
