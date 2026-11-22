package de.tum.cit.aet.artemis.hyperion.repository;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
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
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolCellCountDTO;
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

    @Query("""
            SELECT item
            FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId
            ORDER BY item.competencyId, item.lectureUnitId, item.language, item.questionType, item.difficulty, item.itemIndex
            """)
    List<QuizQuestionPoolItem> findAllByCourseIdOrdered(@Param("courseId") long courseId);

    @Query("""
            SELECT item
            FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId AND item.competencyId = :competencyId
            ORDER BY item.lectureUnitId, item.language, item.questionType, item.difficulty, item.itemIndex
            """)
    List<QuizQuestionPoolItem> findAllByCourseIdAndCompetencyIdOrdered(@Param("courseId") long courseId, @Param("competencyId") long competencyId);

    /**
     * Counts the items of a course per cell. Cells without items are absent from the result.
     *
     * @param courseId the course
     * @return one count per cell that holds at least one item
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolCellCountDTO(item.competencyId, item.lectureUnitId, item.language, item.questionType, item.difficulty,
                COUNT(item))
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

    @Query("""
            SELECT new de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolStateCountDTO(item.state, COUNT(item))
            FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId
            GROUP BY item.state
            """)
    List<QuizQuestionPoolStateCountDTO> countByStateForCourse(@Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(item)
            FROM QuizQuestionPoolItem item
            WHERE item.course.id = :courseId AND item.accepted = :accepted
            """)
    int countByCourseIdAndAccepted(@Param("courseId") long courseId, @Param("accepted") boolean accepted);
}
