package de.tum.cit.aet.artemis.repository.iris;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.cit.aet.artemis.domain.iris.message.IrisMessage;
import de.tum.cit.aet.artemis.domain.iris.message.IrisMessageSender;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the IrisMessage entity.
 */
public interface IrisMessageRepository extends ArtemisJpaRepository<IrisMessage, Long> {

    List<IrisMessage> findAllBySessionId(long sessionId);

    /**
     * Counts the number of LLM responses the user got within the given timeframe.
     *
     * @param userId the id of the user
     * @param start  the start of the timeframe
     * @param end    the end of the timeframe
     * @return the number of chat messages sent by the user within the given timeframe
     */
    @Query("""
            SELECT COUNT(DISTINCT m)
            FROM IrisMessage m
                JOIN TREAT (m.session AS IrisChatSession) s
            WHERE s.user.id = :userId
                AND m.sender = de.tum.cit.aet.artemis.domain.iris.message.IrisMessageSender.LLM
                AND m.sentAt BETWEEN :start AND :end
            """)
    int countLlmResponsesOfUserWithinTimeframe(@Param("userId") long userId, @Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);

    Optional<IrisMessage> findFirstBySessionIdAndSenderOrderBySentAtDesc(long sessionId, @NotNull IrisMessageSender sender);

    @EntityGraph(type = LOAD, attributePaths = { "content" })
    IrisMessage findIrisMessageById(long irisMessageId);

    /**
     * Finds the first message with content by session ID and sender, ordered by the sent date in descending order.
     * This method avoids in-memory paging by retrieving the message directly from the database.
     *
     * @param sessionId the ID of the session to find the message for
     * @param sender    the sender of the message
     * @return the first {@code IrisMessage} with content, ordered by sent date in descending order,
     *         or null if no message is found
     */
    default IrisMessage findFirstWithContentBySessionIdAndSenderOrderBySentAtDesc(long sessionId, @NotNull IrisMessageSender sender) {
        var irisMessage = findFirstBySessionIdAndSenderOrderBySentAtDesc(sessionId, sender);
        if (irisMessage.isEmpty()) {
            return null;
        }
        return findIrisMessageById(irisMessage.get().getId());
    }
}
