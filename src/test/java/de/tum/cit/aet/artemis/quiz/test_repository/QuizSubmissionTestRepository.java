package de.tum.cit.aet.artemis.quiz.test_repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.repository.QuizSubmissionRepository;

@Repository
@Primary
public interface QuizSubmissionTestRepository extends QuizSubmissionRepository {

    Set<QuizSubmission> findByParticipation_Exercise_Id(long exerciseId);

    @Query("""
            SELECT submission
            FROM QuizSubmission submission
                JOIN submission.participation participation
                JOIN participation.exercise exercise
            WHERE exercise.id = :quizExerciseId
            """)
    Optional<QuizSubmission> findByQuizExerciseId(@Param("quizExerciseId") long quizExerciseId);
}
