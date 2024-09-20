package de.tum.cit.aet.artemis.exercise.test_repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;

@Repository
public interface SubmissionTestRepository extends SubmissionRepository {

    /**
     * Calculate the number of submitted submissions for the given exercise. This query uses the participations to make sure that each student is only counted once
     *
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true
     */
    @Query("""
            SELECT COUNT(DISTINCT p)
            FROM StudentParticipation p
                JOIN p.submissions s
            WHERE p.exercise.id = :exerciseId
                AND s.submitted = TRUE
            """)
    long countByExerciseIdSubmitted(@Param("exerciseId") long exerciseId);

    /**
     * Gets all submitted Submissions for the given exercise. Note that you usually only want the latest submissions.
     *
     * @param exerciseId the ID of the exercise
     * @return Set of Submissions
     */
    Set<Submission> findByParticipation_ExerciseIdAndSubmittedIsTrue(long exerciseId);

    List<Submission> findByParticipation_Exercise_ExerciseGroup_Exam_Id(long examId);
}
