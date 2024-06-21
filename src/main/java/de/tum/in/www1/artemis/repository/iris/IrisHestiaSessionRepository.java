package de.tum.in.www1.artemis.repository.iris;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;

import de.tum.in.www1.artemis.domain.iris.session.IrisHestiaSession;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

/**
 * Repository interface for managing {@link IrisHestiaSession} entities.
 * Provides custom queries for finding hestia sessions based on different criteria.
 */
public interface IrisHestiaSessionRepository extends ArtemisJpaRepository<IrisHestiaSession, Long> {

    /**
     * Finds a list of {@link IrisHestiaSession} based on the exercise and user IDs.
     *
     * @param codeHintId The ID of the code hint.
     * @return A list of hestia sessions sorted by creation date in descending order.
     */
    List<IrisHestiaSession> findByCodeHintIdOrderByCreationDateDesc(Long codeHintId);

    /**
     * Finds a single {@link IrisHestiaSession} by its ID and eagerly loads all messages and their contents,
     * as well as the code hint and its solution entries
     *
     * @param sessionId The ID of the session to find
     * @return The session with the given ID
     */
    @EntityGraph(type = LOAD, attributePaths = { "messages", "messages.content", "codeHint", "codeHint.exercise", "codeHint.solutionEntries" })
    IrisHestiaSession findWithMessagesAndContentsAndCodeHintById(long sessionId);
}
