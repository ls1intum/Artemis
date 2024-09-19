package de.tum.cit.aet.artemis.core.test_repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.repository.TutorParticipationRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Repository
public interface TutorParticipationTestRepository extends TutorParticipationRepository {

    List<TutorParticipation> findByAssessedExercise(Exercise assessedExercise);

    List<TutorParticipation> findAllByAssessedExercise_Course(Course course);

}
