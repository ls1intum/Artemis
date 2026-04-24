package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

/**
 * Spring Data JPA repository for the QuizBatch entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface QuizBatchRepository extends ArtemisJpaRepository<QuizBatch, Long> {

    Set<QuizBatch> findAllByQuizExercise(QuizExercise quizExercise);

    Set<QuizBatch> findAllByQuizExerciseAndCreator(QuizExercise quizExercise, Long creator);

    Optional<QuizBatch> findByQuizExerciseAndPassword(QuizExercise quizExercise, String password);

    Optional<QuizBatch> findFirstByQuizExercise(QuizExercise quizExercise);

    /**
     * Retrieve QuizBatch for given quiz exercise and studentLogin
     *
     * @param quizExercise the quiz exercise for which QuizBatch is to be retrieved
     * @param studentLogin the login of the student for which QuizBatch is to be retrieved
     * @return QuizBatch for given quiz exercise and studentLogin
     */
    @Query("""
            SELECT quizBatch
            FROM QuizBatch quizBatch
                JOIN QuizSubmission submission ON quizBatch.id = submission.quizBatch
                JOIN TREAT(submission.participation AS StudentParticipation) participation
            WHERE participation.exercise = :quizExercise
                AND participation.student.login = :studentLogin
            """)
    Optional<QuizBatch> findByQuizExerciseAndStudentLogin(@Param("quizExercise") QuizExercise quizExercise, @Param("studentLogin") String studentLogin);

    /**
     * Targeted UPDATE of a single batch's startTime. Used by START_NOW instead of {@code save(quizBatch)} to avoid
     * rippling into the cascaded parent save of the quiz exercise.
     *
     * @param id        the id of the batch to update
     * @param startTime the new start time for the batch
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE QuizBatch b
            SET b.startTime = :startTime
            WHERE b.id = :id
            """)
    void updateStartTime(@Param("id") Long id, @Param("startTime") ZonedDateTime startTime);

    /**
     * Clamp every batch's startTime so it cannot start later than the last moment compatible with the new dueDate,
     * used by END_NOW. Mirrors {@link de.tum.cit.aet.artemis.quiz.service.QuizBatchService#quizBatchStartDate} but
     * evaluated entirely in the database as a single statement.
     * <ul>
     * <li>If the batch has no startTime yet, leave it untouched.</li>
     * <li>If {@code lastStart} is before the batch's current startTime, pull the batch back to {@code lastStart}.</li>
     * <li>Otherwise leave it untouched.</li>
     * </ul>
     *
     * @param quizExerciseId the exercise whose batches should be clamped
     * @param lastStart      the latest permissible start time given the new dueDate and the quiz duration (newDueDate − duration − gracePeriod)
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE QuizBatch b
            SET b.startTime = CASE
                WHEN b.startTime IS NULL THEN b.startTime
                WHEN :lastStart < b.startTime THEN :lastStart
                ELSE b.startTime
            END
            WHERE b.quizExercise.id = :quizExerciseId
            """)
    void clampBatchStartTimesForEndNow(@Param("quizExerciseId") Long quizExerciseId, @Param("lastStart") ZonedDateTime lastStart);
}
