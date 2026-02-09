package de.tum.cit.aet.artemis.iris.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Collections;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;

@Repository
@Conditional(IrisEnabled.class)
@Lazy
public interface IrisTutorSuggestionSessionRepository extends ArtemisJpaRepository<IrisTutorSuggestionSession, Long> {

    List<IrisTutorSuggestionSession> findByPostIdAndUserIdOrderByCreationDateDesc(Long postId, Long userId, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = "messages")
    List<IrisTutorSuggestionSession> findSessionsWithMessagesByIdIn(List<Long> ids);

    /**
     * Finds the latest sessions for the given post and user with messages.
     *
     * @param postId   the id of the post
     * @param userId   the id of the user
     * @param pageable the pageable to use for the query
     * @return the latest sessions for the given post and user with messages
     */
    default List<IrisTutorSuggestionSession> findLatestSessionsByPostIdAndUserIdWithMessages(Long postId, Long userId, Pageable pageable) {
        List<Long> ids = findByPostIdAndUserIdOrderByCreationDateDesc(postId, userId, pageable).stream().map(DomainObject::getId).toList();

        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        return findSessionsWithMessagesByIdIn(ids);
    }
}
