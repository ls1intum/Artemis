package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ExampleSubmission;

/**
 * Spring Data JPA repository for the ExampleSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ExampleSubmissionRepository extends JpaRepository<ExampleSubmission, Long> {

    Long countAllByExerciseId(long exerciseId);

    List<ExampleSubmission> findAllByExerciseId(long exerciseId);

    List<ExampleSubmission> findAllByExerciseIdAndUsedForTutorial(Long exercise_id, Boolean usedForTutorial);

    @EntityGraph(type = LOAD, attributePaths = { "submission", "submission.results" })
    Set<ExampleSubmission> findAllWithEagerResultByExerciseId(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submission", "submission.results" })
    @Query("select distinct exampleSubmission from ExampleSubmission exampleSubmission left join fetch exampleSubmission.tutorParticipations where exampleSubmission.id = :#{#exampleSubmissionId}")
    Optional<ExampleSubmission> findByIdWithEagerTutorParticipations(@Param("exampleSubmissionId") Long exampleSubmissionId);

    @Query("select distinct exampleSubmission from ExampleSubmission exampleSubmission left join fetch exampleSubmission.submission s left join fetch s.results r left join fetch r.feedbacks where exampleSubmission.id = :#{#exampleSubmissionId}")
    Optional<ExampleSubmission> findByIdWithEagerResultAndFeedback(@Param("exampleSubmissionId") Long exampleSubmissionId);

    Optional<ExampleSubmission> findBySubmissionId(@Param("submissionId") Long submissionId);

    @Query("select distinct exampleSubmission from ExampleSubmission exampleSubmission left join fetch exampleSubmission.exercise s left join fetch s.gradingCriteria where exampleSubmission.id = :#{#exampleSubmissionId}")
    Optional<ExampleSubmission> findByIdWithEagerExercise(@Param("exampleSubmissionId") Long exampleSubmissionId);
}
