package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;

/**
 * Spring Data JPA repository for the TutorParticipation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TutorParticipationRepository extends JpaRepository<TutorParticipation, Long> {

    List<TutorParticipation> findByAssessedExercise(Exercise assessedExercise);

    TutorParticipation findByAssessedExerciseAndTutor(Exercise assessedExercise, User tutor);

    List<TutorParticipation> findAllByAssessedExercise_Course_IdAndTutor_Id(long courseId, long tutorId);
}
