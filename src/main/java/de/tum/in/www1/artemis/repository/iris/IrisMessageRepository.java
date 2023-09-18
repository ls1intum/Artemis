package de.tum.in.www1.artemis.repository.iris;

import java.util.List;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the IrisMessage entity.
 */
public interface IrisMessageRepository extends JpaRepository<IrisMessage, Long> {

    List<IrisMessage> findAllBySessionId(Long sessionId);

    @Query("""
            SELECT DISTINCT m
            FROM IrisMessage m
            LEFT JOIN FETCH m.content
            WHERE m.session.id = :sessionId
            AND m.sender <> 'ARTEMIS'
            """)
    List<IrisMessage> findAllExceptSystemMessagesWithContentBySessionId(Long sessionId);

    @NotNull
    default IrisMessage findByIdElseThrow(long messageId) throws EntityNotFoundException {
        return findById(messageId).orElseThrow(() -> new EntityNotFoundException("Iris Message", messageId));
    }
}
