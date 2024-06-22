package de.tum.in.www1.artemis.repository.iris;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

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
                AND m.sender = de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender.LLM
                AND m.sentAt BETWEEN :start AND :end
            """)
    int countLlmResponsesOfUserWithinTimeframe(@Param("userId") long userId, @Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);

    @NotNull
    default IrisMessage findByIdElseThrow(long messageId) throws EntityNotFoundException {
        return findById(messageId).orElseThrow(() -> new EntityNotFoundException("Iris Message", messageId));
    }

    Optional<IrisMessage> findFirstBySessionIdAndSenderOrderBySentAtDesc(long sessionId, @NotNull IrisMessageSender sender);

    @EntityGraph(type = LOAD, attributePaths = { "content" })
    IrisMessage findIrisMessageById(long irisMessageId);

    default IrisMessage findFirstWithContentBySessionIdAndSenderOrderBySentAtDesc(long sessionId, @NotNull IrisMessageSender sender) {
        var irisMessage = findFirstBySessionIdAndSenderOrderBySentAtDesc(sessionId, sender);
        if (irisMessage.isEmpty()) {
            return null;
        }
        return findIrisMessageById(irisMessage.get().getId());
    }
}
