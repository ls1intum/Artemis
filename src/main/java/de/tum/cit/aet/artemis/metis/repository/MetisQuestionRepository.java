package de.tum.cit.aet.artemis.metis.repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;

/**
 * Spring Data JPA repository for the {@link MetisQuestion} entity, the main and follow-up questions of a Metis check with their answers and ratings.
 * <p>
 * Every change of a question after its generation is a guarded {@code UPDATE}: the state the change starts from is part of the {@code WHERE} clause, and the
 * caller acts on the number of rows the statement changed, so that an answer, a rating or an edit is written once even when two requests race. The statements
 * bypass the persistence context, so callers read a question again after changing it.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Repository
public interface MetisQuestionRepository extends ArtemisJpaRepository<MetisQuestion, Long> {

    /**
     * All questions of one check, main questions and follow-ups alike, in display order.
     *
     * @param checkId the check the questions belong to
     * @return the questions ordered by {@code questionOrder}, empty while none have been generated
     */
    List<MetisQuestion> findAllByCheckIdOrderByQuestionOrderAsc(long checkId);

    /**
     * All questions of all checks of an exercise in one statement, for the dashboard and the export.
     *
     * @param exerciseId the exercise
     * @return the questions, ordered by check and then by their order inside the check
     */
    @Query("""
            SELECT q
            FROM MetisQuestion q
                JOIN MetisCheck c ON c.id = q.checkId
                JOIN StudentParticipation p ON p.id = c.participationId
            WHERE p.exercise.id = :exerciseId
            ORDER BY q.checkId ASC, q.questionOrder ASC
            """)
    List<MetisQuestion> findAllByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * One question, provided it belongs to a check of the given exercise. An endpoint that authorizes the caller for an exercise reads the question through
     * this method, so that a question id of another exercise is not found.
     *
     * @param questionId the question
     * @param exerciseId the exercise the caller is authorized for
     * @return the question, or empty when it does not exist or belongs to another exercise
     */
    @Query("""
            SELECT q
            FROM MetisQuestion q
                JOIN MetisCheck c ON c.id = q.checkId
                JOIN StudentParticipation p ON p.id = c.participationId
            WHERE q.id = :questionId
                AND p.exercise.id = :exerciseId
            """)
    Optional<MetisQuestion> findByIdAndExerciseId(@Param("questionId") long questionId, @Param("exerciseId") long exerciseId);

    /**
     * One question of an exercise, or an exception when there is none.
     *
     * @param questionId the question
     * @param exerciseId the exercise the caller is authorized for
     * @return the question
     * @throws de.tum.cit.aet.artemis.core.exception.EntityNotFoundException when the question does not exist or belongs to another exercise
     */
    default MetisQuestion findByIdAndExerciseIdElseThrow(long questionId, long exerciseId) {
        return getValueElseThrow(findByIdAndExerciseId(questionId, exerciseId), questionId);
    }

    /**
     * The answered questions whose rating is missing, for the rating backfill. Only exercises whose check is enabled count, and only checks started after the
     * given time, so that the backfill also covers an exercise whose last window lies long after its due date. An answer younger than the bound may still be
     * waiting for its rating in the request that stored it and is left alone.
     *
     * @param startedAfter   checks started at or before this time are not considered
     * @param answeredBefore only answers given before this time are returned
     * @param excludedIds    question ids to leave out, those being rated and those given up on; never empty, the caller adds an id that does not exist
     * @param pageable       how many ids to return
     * @return the ids of the unrated answered questions, in ascending order
     */
    @Query("""
            SELECT q.id
            FROM MetisQuestion q
                JOIN MetisCheck c ON c.id = q.checkId
                JOIN StudentParticipation p ON p.id = c.participationId
                JOIN MetisExerciseConfig cfg ON cfg.exerciseId = p.exercise.id
            WHERE cfg.enabled = TRUE
                AND c.startedAt > :startedAfter
                AND q.answeredAt IS NOT NULL
                AND q.llmRatedAt IS NULL
                AND q.answeredAt < :answeredBefore
                AND q.id NOT IN :excludedIds
            ORDER BY q.id ASC
            """)
    List<Long> findUnratedAnsweredQuestionIds(@Param("startedAfter") ZonedDateTime startedAfter, @Param("answeredBefore") ZonedDateTime answeredBefore,
            @Param("excludedIds") Collection<Long> excludedIds, Pageable pageable);

    /**
     * Approves a main question during the review. A follow-up is never reviewed, and an approved question keeps the time of its first approval.
     *
     * @param questionId the main question
     * @param approvedAt when it was approved
     * @return 1 if the question was approved now, 0 if it is a follow-up or was approved before
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisQuestion q
            SET q.approvedAt = :approvedAt
            WHERE q.id = :questionId
                AND q.parentQuestionId IS NULL
                AND q.approvedAt IS NULL
            """)
    int approve(@Param("questionId") long questionId, @Param("approvedAt") ZonedDateTime approvedAt);

    /**
     * Approves every main question of an exercise that is not approved yet.
     *
     * @param exerciseId the exercise
     * @param approvedAt when they were approved
     * @return the number of approved questions
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisQuestion q
            SET q.approvedAt = :approvedAt
            WHERE q.parentQuestionId IS NULL
                AND q.approvedAt IS NULL
                AND q.checkId IN (
                    SELECT c.id
                    FROM MetisCheck c
                        JOIN StudentParticipation p ON p.id = c.participationId
                    WHERE p.exercise.id = :exerciseId
                )
            """)
    int approveAllOpenMainQuestionsOfExercise(@Param("exerciseId") long exerciseId, @Param("approvedAt") ZonedDateTime approvedAt);

    /**
     * Replaces the text of a main question during the review, keeping its approval. Once the student has seen the question, or has started the check, the
     * questions of the check are frozen, so that what the student answers is what the instructor last saw.
     *
     * @param questionId   the main question
     * @param questionText the new text
     * @return 1 if the text was replaced, 0 if the question is a follow-up, has been shown, or its check is started or neither ready nor in review
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisQuestion q
            SET q.questionText = :questionText
            WHERE q.id = :questionId
                AND q.parentQuestionId IS NULL
                AND q.shownAt IS NULL
                AND EXISTS (
                    SELECT c.id
                    FROM MetisCheck c
                    WHERE c.id = q.checkId
                        AND c.startedAt IS NULL
                        AND c.status IN (de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.READY, de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.IN_REVIEW)
                )
            """)
    int updateTextIfNotShown(@Param("questionId") long questionId, @Param("questionText") String questionText);

    /**
     * Stores a regenerated question: its new text and time limit, one more regeneration, and no approval, since nobody has reviewed the new text. The count the
     * caller read before the LLM call guards against a second regeneration or edit that finished meanwhile.
     *
     * @param questionId                the main question
     * @param questionText              the new text
     * @param timeLimitSeconds          the new time limit
     * @param expectedRegenerationCount the regeneration count the caller read before it asked the LLM
     * @return 1 if the question was replaced, 0 if it changed meanwhile, has been shown, or its check is started or neither ready nor in review
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisQuestion q
            SET q.questionText = :questionText,
                q.timeLimitSeconds = :timeLimitSeconds,
                q.regenerationCount = q.regenerationCount + 1,
                q.approvedAt = NULL
            WHERE q.id = :questionId
                AND q.regenerationCount = :expectedRegenerationCount
                AND q.shownAt IS NULL
                AND EXISTS (
                    SELECT c.id
                    FROM MetisCheck c
                    WHERE c.id = q.checkId
                        AND c.startedAt IS NULL
                        AND c.status IN (de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.READY, de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.IN_REVIEW)
                )
            """)
    int replaceAfterRegeneration(@Param("questionId") long questionId, @Param("questionText") String questionText, @Param("timeLimitSeconds") int timeLimitSeconds,
            @Param("expectedRegenerationCount") int expectedRegenerationCount);

    /**
     * Shows a question to its student, which starts its timer on the server.
     *
     * @param questionId the question
     * @param shownAt    when it was shown
     * @return 1 if the question was shown now, 0 if it had been shown before
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisQuestion q
            SET q.shownAt = :shownAt
            WHERE q.id = :questionId
                AND q.shownAt IS NULL
            """)
    int markShown(@Param("questionId") long questionId, @Param("shownAt") ZonedDateTime shownAt);

    /**
     * Stores the student's answer to a shown question. A question is answered once; a repeated or parallel submission changes nothing.
     *
     * @param questionId the shown question
     * @param answerText the answer, possibly empty
     * @param answeredAt when the answer counts as given
     * @return 1 if the answer was stored, 0 if the question has not been shown or was answered before
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisQuestion q
            SET q.answerText = :answerText,
                q.answeredAt = :answeredAt
            WHERE q.id = :questionId
                AND q.shownAt IS NOT NULL
                AND q.answeredAt IS NULL
            """)
    int saveAnswer(@Param("questionId") long questionId, @Param("answerText") String answerText, @Param("answeredAt") ZonedDateTime answeredAt);

    /**
     * Stores the LLM's rating of an answer. An answer is rated once: of the live rating and the backfill, whichever writes first wins.
     *
     * @param questionId the answered question
     * @param score      the score on the configured Likert scale
     * @param rationale  the reasoning for the instructor
     * @param ratedAt    when the rating was stored
     * @return 1 if the rating was stored, 0 if the question has no answer or was rated before
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisQuestion q
            SET q.llmScore = :score,
                q.llmRationale = :rationale,
                q.llmRatedAt = :ratedAt
            WHERE q.id = :questionId
                AND q.answeredAt IS NOT NULL
                AND q.llmRatedAt IS NULL
            """)
    int saveRating(@Param("questionId") long questionId, @Param("score") int score, @Param("rationale") String rationale, @Param("ratedAt") ZonedDateTime ratedAt);

    /**
     * Deletes every question of a check, the leftovers of an interrupted generation, before the check is generated again. A bulk statement rather than a
     * derived delete, which would load and delete row by row. It only runs for checks that were never started, so no follow-ups exist.
     *
     * @param checkId the check
     * @return the number of deleted questions
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM MetisQuestion q
            WHERE q.checkId = :checkId
            """)
    int deleteAllOfCheck(@Param("checkId") long checkId);
}
