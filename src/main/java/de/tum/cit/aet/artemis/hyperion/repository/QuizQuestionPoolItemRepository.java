package de.tum.cit.aet.artemis.hyperion.repository;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItemState;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionReviewState;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolCellCountDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolLectureUnitCountDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolStateCountDTO;

/**
 * Spring Data repository for the quiz question pool.
 * <p>
 * Claims name the state and the version they expect, and every write increments the version, so a worker whose claim another
 * node released as stale and took over cannot overwrite the newer state. A model call that outlasts the stale-claim age can still
 * be repeated by the node that took the item over; the earlier worker's result is then discarded.
 */
@Lazy
@Repository
@Conditional(HyperionEnabled.class)
public interface QuizQuestionPoolItemRepository extends ArtemisJpaRepository<QuizQuestionPoolItem, Long> {

    /**
     * Returns one page of a course's items in pool order, optionally only those of one competency.
     *
     * @param courseId     the course
     * @param competencyId the competency to restrict to, or {@code null} for the whole pool
     * @param pageable     the page to return
     * @return the page
     */
    @Query(value = """
            SELECT item
            FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId AND (:competencyId IS NULL OR item.competencyId = :competencyId)
            ORDER BY item.competencyId, item.lectureUnitId, item.language, item.questionType, item.difficulty, item.itemIndex
            """, countQuery = """
            SELECT COUNT(item)
            FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId AND (:competencyId IS NULL OR item.competencyId = :competencyId)
            """)
    Page<QuizQuestionPoolItem> findPageForCourse(@Param("courseId") long courseId, @Param("competencyId") @Nullable Long competencyId, Pageable pageable);

    /**
     * Returns one page of the items flagged for review, those awaiting a decision first and then in pool order.
     *
     * @param courseId the course
     * @param pageable the page to return
     * @return the page
     */
    @Query(value = """
            SELECT item
            FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId AND item.flagged = TRUE
            ORDER BY CASE WHEN item.reviewState = de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionReviewState.PENDING THEN 0 ELSE 1 END, item.competencyId,
                item.lectureUnitId, item.language, item.questionType, item.difficulty, item.itemIndex
            """, countQuery = """
            SELECT COUNT(item)
            FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId AND item.flagged = TRUE
            """)
    Page<QuizQuestionPoolItem> findFlaggedPageForCourse(@Param("courseId") long courseId, Pageable pageable);

    /**
     * Counts the items of a course that are flagged for review and still await a decision.
     *
     * @param courseId the course
     * @return the number of such items
     */
    @Query("""
            SELECT COUNT(item)
            FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId AND item.flagged = TRUE AND item.reviewState = de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionReviewState.PENDING
            """)
    long countFlaggedPendingForCourse(@Param("courseId") long courseId);

    /**
     * Finds the judged items of a course whose review flag has not been stored yet.
     *
     * @param courseId the course
     * @param state    the judged state
     * @return the items
     */
    @Query("""
            SELECT item
            FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId AND item.state = :state AND item.flagged IS NULL
            """)
    List<QuizQuestionPoolItem> findAllWithoutFlagForCourse(@Param("courseId") long courseId, @Param("state") QuizQuestionPoolItemState state);

    /**
     * Stores the review flag of one item without touching its version, so that a build working on the item is not disturbed.
     *
     * @param itemId  the item
     * @param flagged whether the item is flagged for review
     * @return the number of updated rows
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE QuizQuestionPoolItem item
            SET item.flagged = :flagged
            WHERE item.id = :itemId AND item.flagged IS NULL
            """)
    int storeFlag(@Param("itemId") long itemId, @Param("flagged") boolean flagged);

    /**
     * Deletes every item of a course, or every item of one of its competencies.
     *
     * @param courseId     the course
     * @param competencyId the competency to clear, or {@code null} for the whole pool
     * @return the number of deleted items
     */
    @Modifying
    @Transactional // ok because of the delete
    @Query("""
            DELETE FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId AND (:competencyId IS NULL OR item.competencyId = :competencyId)
            """)
    int deleteAllForCourse(@Param("courseId") long courseId, @Param("competencyId") @Nullable Long competencyId);

    /**
     * Deletes every item of a course that is in one state.
     *
     * @param courseId the course
     * @param state    the state
     * @return the number of deleted items
     */
    @Modifying
    @Transactional // ok because of the delete
    @Query("""
            DELETE FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId AND item.state = :state
            """)
    int deleteAllByCourseIdAndState(@Param("courseId") long courseId, @Param("state") QuizQuestionPoolItemState state);

    /**
     * Lists the competencies that have items in a course's pool.
     *
     * @param courseId the course
     * @return the competency ids in ascending order
     */
    @Query("""
            SELECT DISTINCT item.competencyId
            FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId
            ORDER BY item.competencyId
            """)
    List<Long> findCompetencyIdsForCourse(@Param("courseId") long courseId);

    /**
     * Counts the items of a course per cell whose material is unchanged, together with the highest index any item of the cell uses.
     * Cells without items are absent from the result.
     *
     * @param courseId the course
     * @return one entry per cell that holds at least one item
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolCellCountDTO(item.competencyId, item.lectureUnitId, item.language, item.questionType, item.difficulty,
                SUM(CASE WHEN item.materialChanged = FALSE THEN 1 ELSE 0 END), MAX(item.itemIndex))
            FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId
            GROUP BY item.competencyId, item.lectureUnitId, item.language, item.questionType, item.difficulty
            """)
    List<QuizQuestionPoolCellCountDTO> countByCellForCourse(@Param("courseId") long courseId);

    /**
     * Finds the items of a course in a state, ordered by cell and index, so the first result names the next cell to work on.
     *
     * @param courseId the course
     * @param state    the state to look for
     * @param pageable limits the result
     * @return the items, oldest cell first
     */
    @Query("""
            SELECT item
            FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId AND item.state = :state
            ORDER BY item.competencyId, item.lectureUnitId, item.language, item.questionType, item.difficulty, item.itemIndex
            """)
    List<QuizQuestionPoolItem> findAllByCourseIdAndState(@Param("courseId") long courseId, @Param("state") QuizQuestionPoolItemState state, Pageable pageable);

    @Query("""
            SELECT item
            FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId AND item.competencyId = :competencyId AND item.lectureUnitId = :lectureUnitId AND item.language = :language
                AND item.questionType = :questionType AND item.difficulty = :difficulty AND item.state = :state
            ORDER BY item.itemIndex
            """)
    List<QuizQuestionPoolItem> findAllInCellWithState(@Param("courseId") long courseId, @Param("competencyId") long competencyId, @Param("lectureUnitId") long lectureUnitId,
            @Param("language") QuizQuestionGenerationLanguage language, @Param("questionType") QuizQuestionGenerationType questionType,
            @Param("difficulty") QuizQuestionDifficulty difficulty, @Param("state") QuizQuestionPoolItemState state, Pageable pageable);

    /**
     * Moves one item from an expected state into a claimed state.
     *
     * @param itemId  the item
     * @param from    the state the item must be in
     * @param to      the claimed state to move it to
     * @param now     the claim time
     * @param version the item's version as the caller read it; the claim fails when another write changed the item since
     * @return 1 when this caller won the claim, 0 when the item was no longer in {@code from} or had changed
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE QuizQuestionPoolItem item
            SET item.state = :to, item.claimedAt = :now, item.updatedAt = :now, item.version = item.version + 1
            WHERE item.id = :itemId AND item.state = :from AND item.version = :version
            """)
    int claim(@Param("itemId") long itemId, @Param("from") QuizQuestionPoolItemState from, @Param("to") QuizQuestionPoolItemState to, @Param("now") ZonedDateTime now,
            @Param("version") long version);

    /**
     * Returns items whose claim is older than {@code before} to the stable state the claim was taken from.
     *
     * @param courseId the course
     * @param from     the claimed state to release
     * @param to       the stable state to return to
     * @param before   claims taken before this time count as stale
     * @param now      the update time
     * @return the number of released items
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE QuizQuestionPoolItem item
            SET item.state = :to, item.claimedAt = NULL, item.updatedAt = :now, item.version = item.version + 1
            WHERE item.course.id = :courseId AND item.state = :from AND item.claimedAt < :before
            """)
    int releaseStaleClaims(@Param("courseId") long courseId, @Param("from") QuizQuestionPoolItemState from, @Param("to") QuizQuestionPoolItemState to,
            @Param("before") ZonedDateTime before, @Param("now") ZonedDateTime now);

    /**
     * Returns items that failed with a transient failure to the stable state before the failed stage, with a fresh attempt budget.
     *
     * @param courseId the course
     * @param from     the failure state to revive
     * @param to       the stable state to return to
     * @param failure  the failure category that counts as transient
     * @param now      the update time
     * @return the number of revived items
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE QuizQuestionPoolItem item
            SET item.state = :to, item.generationAttempts = 0, item.filterAttempts = 0, item.failure = NULL, item.updatedAt = :now, item.version = item.version + 1
            WHERE item.course.id = :courseId AND item.state = :from AND item.failure = :failure
            """)
    int reviveTransientFailures(@Param("courseId") long courseId, @Param("from") QuizQuestionPoolItemState from, @Param("to") QuizQuestionPoolItemState to,
            @Param("failure") String failure, @Param("now") ZonedDateTime now);

    /**
     * Counts the items of a course per state and, for judged items, per filter outcome.
     *
     * @param courseId the course
     * @return one row per state and outcome that occurs
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolStateCountDTO(item.state, item.accepted, COUNT(item))
            FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId
            GROUP BY item.state, item.accepted
            """)
    List<QuizQuestionPoolStateCountDTO> countByStateForCourse(@Param("courseId") long courseId);

    /**
     * Records the review decision on one item.
     *
     * @param itemId      the item
     * @param reviewState the decision
     * @param now         the time of the decision
     * @return the number of updated rows
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE QuizQuestionPoolItem item
            SET item.reviewState = :reviewState, item.updatedAt = :now
            WHERE item.id = :itemId
            """)
    int updateReviewState(@Param("itemId") long itemId, @Param("reviewState") QuizQuestionReviewState reviewState, @Param("now") ZonedDateTime now);

    /**
     * Deletes the items of a lecture unit that do not carry the given review state, so that approved items can be kept while the rest goes.
     *
     * @param courseId      the course
     * @param lectureUnitId the lecture unit
     * @param reviewState   the review state that spares an item
     * @return the number of deleted items
     */
    @Modifying
    @Transactional // ok because of the delete
    @Query("""
            DELETE FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId AND item.lectureUnitId = :lectureUnitId AND item.reviewState <> :reviewState
            """)
    int deleteAllByLectureUnitExceptReviewState(@Param("courseId") long courseId, @Param("lectureUnitId") long lectureUnitId,
            @Param("reviewState") QuizQuestionReviewState reviewState);

    /**
     * Flags the remaining items of a lecture unit as generated from material that has changed since.
     *
     * @param courseId      the course
     * @param lectureUnitId the lecture unit
     * @param now           the update time
     * @return the number of flagged items
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE QuizQuestionPoolItem item
            SET item.materialChanged = TRUE, item.updatedAt = :now, item.version = item.version + 1
            WHERE item.course.id = :courseId AND item.lectureUnitId = :lectureUnitId AND item.materialChanged = FALSE
            """)
    int markMaterialChangedByLectureUnit(@Param("courseId") long courseId, @Param("lectureUnitId") long lectureUnitId, @Param("now") ZonedDateTime now);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolLectureUnitCountDTO(item.lectureUnitId, COUNT(item),
                SUM(CASE WHEN item.reviewState = :approved THEN 1 ELSE 0 END), SUM(CASE WHEN item.materialChanged = TRUE THEN 1 ELSE 0 END))
            FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId
            GROUP BY item.lectureUnitId
            """)
    List<QuizQuestionPoolLectureUnitCountDTO> countByLectureUnitForCourse(@Param("courseId") long courseId, @Param("approved") QuizQuestionReviewState approved);
}
