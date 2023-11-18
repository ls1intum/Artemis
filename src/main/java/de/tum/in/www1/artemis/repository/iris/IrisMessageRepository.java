package de.tum.in.www1.artemis.repository.iris;

import java.time.ZonedDateTime;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the IrisMessage entity.
 */
public interface IrisMessageRepository extends JpaRepository<IrisMessage, Long> {

    List<IrisMessage> findAllBySessionId(@Param("sessionId") Long sessionId);

    /**
     * Counts the number of LLM responses the user got within the given timeframe.
     * FIXME: The query needs to distinguish between different types of IrisSession to avoid overlapping rate limits.
     *
     * @param userId the id of the user
     * @param start  the start of the timeframe
     * @param end    the end of the timeframe
     * @return the number of chat messages sent by the user within the given timeframe
     */
    @Query("""
            SELECT COUNT(DISTINCT m)
            FROM IrisMessage m
                LEFT JOIN m.session as s
            WHERE type(s) = de.tum.in.www1.artemis.domain.iris.session.IrisSession
                AND s.user.id = :userId
                AND m.sender = 'LLM'
                AND m.sentAt BETWEEN :start AND :end
            """)
    int countLlmResponsesOfUserWithinTimeframe(@Param("userId") Long userId, @Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);

    @NotNull
    default IrisMessage findByIdElseThrow(long messageId) throws EntityNotFoundException {
        return findById(messageId).orElseThrow(() -> new EntityNotFoundException("Iris Message", messageId));
    }
}
