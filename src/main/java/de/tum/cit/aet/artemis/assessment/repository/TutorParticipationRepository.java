package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Spring Data JPA repository for the TutorParticipation entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface TutorParticipationRepository extends ArtemisJpaRepository<TutorParticipation, Long> {

    List<TutorParticipation> findByAssessedExercise(Exercise assessedExercise);

    @EntityGraph(type = LOAD, attributePaths = { "trainedExampleSubmissions", "trainedExampleSubmissions.submission.results" })
    TutorParticipation findWithEagerExampleSubmissionAndResultsByAssessedExerciseAndTutor(Exercise assessedExercise, User tutor);

    Boolean existsByAssessedExerciseIdAndTutorId(Long assessedExerciseId, Long tutorId);

    @EntityGraph(type = LOAD, attributePaths = { "trainedExampleSubmissions", "trainedExampleSubmissions.submission.results" })
    List<TutorParticipation> findAllByAssessedExercise_Course_IdAndTutor_Id(long courseId, long tutorId);

    List<TutorParticipation> findAllByAssessedExercise_Course(Course course);

    @EntityGraph(type = LOAD, attributePaths = { "trainedExampleSubmissions", "trainedExampleSubmissions.submission.results" })
    List<TutorParticipation> findAllByAssessedExercise_ExerciseGroup_Exam_IdAndTutor_Id(long examId, long tutorId);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByAssessedExerciseId(long assessedExerciseId);
}
