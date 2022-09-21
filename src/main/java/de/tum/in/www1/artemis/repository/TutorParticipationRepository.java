package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Course;
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

    @EntityGraph(type = LOAD, attributePaths = { "trainedExampleSubmissions", "trainedExampleSubmissions.submission.results" })
    TutorParticipation findWithEagerExampleSubmissionAndResultsByAssessedExerciseAndTutor(Exercise assessedExercise, User tutor);

    Boolean existsByAssessedExerciseIdAndTutorId(Long assessedExerciseId, Long tutorId);

    @EntityGraph(type = LOAD, attributePaths = { "trainedExampleSubmissions", "trainedExampleSubmissions.submission.results" })
    List<TutorParticipation> findAllByAssessedExercise_Course_IdAndTutor_Id(long courseId, long tutorId);

    List<TutorParticipation> findAllByAssessedExercise_Course(Course course);

    @EntityGraph(type = LOAD, attributePaths = { "trainedExampleSubmissions", "trainedExampleSubmissions.submission.results" })
    List<TutorParticipation> findAllByAssessedExercise_ExerciseGroup_Exam_IdAndTutor_Id(long examId, long tutorId);

    void deleteAllByAssessedExerciseId(long assessedExerciseId);
}
