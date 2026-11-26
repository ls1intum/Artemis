package de.tum.cit.aet.artemis.atlas.repository;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.practice.PracticeLog;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface PracticeLogRepository extends ArtemisJpaRepository<PracticeLog, Long> {

    List<PracticeLog> findAllByPracticeSessionIdOrderByIdAsc(long practiceSessionId);

    /**
     * Marks a practice session as completed unless it already is. The row lock of the update makes a concurrent second submission see the completion.
     *
     * @param sessionId   the practice session
     * @param score       the achieved points
     * @param maxPoints   the reachable points
     * @param completedAt the completion time
     * @return 1 if the session was completed by this call, 0 if it was already completed
     */
    @Modifying
    @Query("""
            UPDATE PracticeSession ps
            SET ps.completedAt = :completedAt,
                ps.score = :score,
                ps.maxPoints = :maxPoints
            WHERE ps.id = :sessionId
                AND ps.completedAt IS NULL
            """)
    int completeSessionIfUnfinished(@Param("sessionId") long sessionId, @Param("score") double score, @Param("maxPoints") double maxPoints,
            @Param("completedAt") ZonedDateTime completedAt);

    /**
     * Completes a practice session and saves its answered question logs together, so a submission is either stored completely or not at all,
     * and only one of several concurrent submissions of the same session is stored.
     *
     * @param sessionId   the practice session
     * @param score       the achieved points
     * @param maxPoints   the reachable points
     * @param completedAt the completion time
     * @param logs        the answered question logs of the session
     * @return whether this call completed the session; false if it had already been completed
     */
    @Transactional // ok because the completion and the answers must be saved together
    default boolean completeSessionWithLogs(long sessionId, double score, double maxPoints, ZonedDateTime completedAt, List<PracticeLog> logs) {
        if (completeSessionIfUnfinished(sessionId, score, maxPoints, completedAt) == 0) {
            return false;
        }
        saveAll(logs);
        return true;
    }
}
