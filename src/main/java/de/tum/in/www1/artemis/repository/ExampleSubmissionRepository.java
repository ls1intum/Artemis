package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.TutorParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


/**
 * Spring Data JPA repository for the ExampleSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ExampleSubmissionRepository extends JpaRepository<ExampleSubmission, Long> {
    List<ExampleSubmission> findAllByExerciseId(long exerciseId);
    List<ExampleSubmission> findAllByExerciseIdAndTutorParticipation(Long exercise_id, TutorParticipation tutorParticipation);
    List<ExampleSubmission> findAllByExerciseIdAndUsedForTutorial(Long exercise_id, Boolean usedForTutorial);
}
