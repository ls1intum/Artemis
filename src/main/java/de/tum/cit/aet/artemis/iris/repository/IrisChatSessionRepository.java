package de.tum.cit.aet.artemis.iris.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.dao.IrisChatSessionDAO;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;

@Lazy
@Repository
@Profile(PROFILE_IRIS)
public interface IrisChatSessionRepository extends ArtemisJpaRepository<IrisChatSession, Long> {

    /**
     * Finds a list of {@link IrisChatSession} based on the course and user ID. Filters sessions without messages and sorts them by creation date in descending order.
     *
     * @param courseId The ID of the course.
     * @param userId   The ID of the user.
     * @param types    A collection of chat session types to filter by.
     * @return A list of chat sessions sorted by creation date in descending order.
     */
    @Query("""
                SELECT new de.tum.cit.aet.artemis.iris.dao.IrisChatSessionDAO(
                                          s,
                                          COALESCE(ccs.courseId, e1.id, e2.id, l.id, -1)
                                      )
                FROM IrisChatSession s
                         LEFT JOIN IrisCourseChatSession ccs ON s.id = ccs.id
                         LEFT JOIN IrisLectureChatSession lcs ON s.id = lcs.id
                         LEFT JOIN IrisTextExerciseChatSession tecs ON s.id = tecs.id
                         LEFT JOIN IrisProgrammingExerciseChatSession pecs ON s.id = pecs.id
                         LEFT JOIN Lecture l ON l.id = lcs.lectureId
                         LEFT JOIN Exercise e1 ON e1.id = tecs.exerciseId
                         LEFT JOIN Exercise e2 ON e2.id = pecs.exerciseId
                         LEFT JOIN s.messages m
                WHERE s.userId = :userId
                  AND TYPE(s) IN (:types)
                  AND (ccs.courseId = :courseId OR l.course.id = :courseId OR e1.course.id = :courseId OR e2.course.id = :courseId)
                GROUP BY s, ccs.courseId, e1.id, e2.id, l.id
                HAVING COUNT(CASE WHEN m.sender = de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender.USER THEN 1 END) > 0
                    OR s.id = (SELECT s2.id
                               FROM IrisChatSession s2
                               WHERE s2.userId = :userId
                               ORDER BY s2.creationDate DESC
                               LIMIT 1)
                ORDER BY s.creationDate DESC
            """)
    List<IrisChatSessionDAO> findByCourseIdAndUserId(@Param("courseId") long courseId, @Param("userId") long userId,
            @Param("types") Collection<Class<? extends IrisChatSession>> types);
}
