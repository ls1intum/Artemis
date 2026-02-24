package de.tum.cit.aet.artemis.iris.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;

@Lazy
@Repository
@Conditional(IrisEnabled.class)
public interface IrisLectureChatSessionRepository extends ArtemisJpaRepository<IrisLectureChatSession, Long> {

    List<IrisLectureChatSession> findByLectureIdAndUserIdOrderByCreationDateDesc(Long lectureId, Long userId);

    List<IrisLectureChatSession> findByLectureIdAndUserIdOrderByCreationDateDesc(Long lectureId, Long userId, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = "messages")
    Optional<IrisLectureChatSession> findSessionWithMessagesByIdAndUserId(Long id, Long userId);

    @EntityGraph(type = LOAD, attributePaths = "messages")
    List<IrisLectureChatSession> findSessionsWithMessagesByIdIn(List<Long> ids);

    /**
     * Finds the latest chat sessions by lecture ID and user ID, including their messages, with pagination support.
     * This method avoids in-memory paging by retrieving the session IDs directly from the database.
     *
     * @param lectureId the ID of the lecture to find the chat sessions for
     * @param userId    the ID of the user to find the chat sessions for
     * @param pageable  the pagination information
     * @return a list of {@code IrisLectureChatSession} with messages, or an empty list if no sessions are found
     */
    default List<IrisLectureChatSession> findLatestSessionsByLectureIdAndUserIdWithMessages(Long lectureId, Long userId, Pageable pageable) {
        List<Long> ids = findByLectureIdAndUserIdOrderByCreationDateDesc(lectureId, userId, pageable).stream().map(DomainObject::getId).toList();

        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        return findSessionsWithMessagesByIdIn(ids);
    }
}
