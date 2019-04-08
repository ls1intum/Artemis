package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.TutorParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


/**
 * Spring Data JPA repository for the ExampleSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ExampleSubmissionRepository extends JpaRepository<ExampleSubmission, Long> {
    List<ExampleSubmission> findAllByExerciseId(long exerciseId);
    List<ExampleSubmission> findAllByExerciseIdAndTutorParticipation(Long exercise_id, TutorParticipation tutorParticipation);
    List<ExampleSubmission> findAllByExerciseIdAndUsedForTutorial(Long exercise_id, Boolean usedForTutorial);
    @Query("select distinct exampleSubmission from ExampleSubmission exampleSubmission left join fetch exampleSubmission.submission s left join fetch s.result r left join fetch r.feedbacks where exampleSubmission.id = :#{#exampleSubmissionId}")
    Optional<ExampleSubmission> findByIdWithEagerResultAndFeedback(@Param("exampleSubmissionId") Long exampleSubmissionId);
}
