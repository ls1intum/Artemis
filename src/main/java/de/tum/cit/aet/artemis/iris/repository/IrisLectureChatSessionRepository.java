package de.tum.cit.aet.artemis.iris.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Collections;
import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;

@Repository
@Profile(PROFILE_IRIS)
public interface IrisLectureChatSessionRepository extends ArtemisJpaRepository<IrisLectureChatSession, Long> {

    @Query("""
                SELECT s
                    FROM IrisLectureChatSession s
                    WHERE s.lecture.id = :lectureId
                        AND s.user.id = :userId
                    ORDER BY s.creationDate DESC
            """)
    List<IrisLectureChatSession> findSessionsByLectureIdAndUserId(@Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("""
                SELECT s
                    FROM IrisLectureChatSession s
                    WHERE s.lecture.id = :lectureId
                        AND s.user.id = :userId
                    ORDER BY s.creationDate DESC
            """)
    List<IrisLectureChatSession> findSessionsByLectureIdAndUserId(@Param("lectureId") Long lectureId, @Param("userId") Long userId, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = "messages")
    List<IrisLectureChatSession> findSessionsWithMessagesByIdIn(List<Long> ids);

    default List<IrisLectureChatSession> findLatestSessionsByLectureIdAndUserIdWithMessages(Long lectureId, Long userId, Pageable pageable) {
        List<Long> ids = findSessionsByLectureIdAndUserId(lectureId, userId, pageable).stream().map(DomainObject::getId).toList();

        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        return findSessionsWithMessagesByIdIn(ids);
    }

    @NotNull
    default List<IrisLectureChatSession> findByLectureIdAndUserIdElseThrow(Long lectureId, Long userId) throws EntityNotFoundException {
        var result = findSessionsByLectureIdAndUserId(lectureId, userId);
        if (result.isEmpty()) {
            throw new EntityNotFoundException("Iris Lecture Chat Session");
        }
        return result;
    }
}
