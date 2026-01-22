package de.tum.cit.aet.artemis.iris.repository;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dao.IrisChatSessionDAO;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;

@Lazy
@Repository
@Conditional(IrisEnabled.class)
public interface IrisChatSessionRepository extends ArtemisJpaRepository<IrisChatSession, Long> {

    /**
     * Finds a list of {@link IrisChatSession} based on the course and user ID. Filters sessions without messages and sorts them by creation date in descending order.
     *
     * @param courseId The ID of the course.
     * @param userId   The ID of the user.
     * @return A list of chat sessions sorted by creation date in descending order.
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.iris.dao.IrisChatSessionDAO(
                      s,
                      COALESCE(ccs.courseId, e1.id, e2.id, l.id, -1),
                      COALESCE(e1.shortName, e2.shortName, l.title)
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
                    LEFT JOIN m.content c
                WHERE s.userId = :userId AND TYPE(s) IN (
                        de.tum.cit.aet.artemis.iris.domain.session.IrisTextExerciseChatSession,
                        de.tum.cit.aet.artemis.iris.domain.session.IrisProgrammingExerciseChatSession,
                        de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession,
                        de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession
                    )
                    AND (ccs.courseId = :courseId OR l.course.id = :courseId OR e1.course.id = :courseId OR e2.course.id = :courseId)
                    AND m.sender = de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender.USER
                GROUP BY s, ccs.courseId, e1.id, e2.id, l.id
                HAVING COUNT(m) > 0
                ORDER BY s.creationDate DESC
            """)
    List<IrisChatSessionDAO> findByCourseIdAndUserId(@Param("courseId") long courseId, @Param("userId") long userId);

    /**
     * Finds all chat sessions for a user with their messages loaded, ordered by creation date.
     * Used for GDPR data export to retrieve all AI tutor interactions for a user.
     *
     * @param userId the ID of the user
     * @return a list of all chat sessions with messages for the user
     */
    @Query("""
            SELECT DISTINCT s
            FROM IrisChatSession s
                LEFT JOIN FETCH s.messages m
                LEFT JOIN FETCH m.content
            WHERE s.userId = :userId
            ORDER BY s.creationDate ASC
            """)
    List<IrisChatSession> findAllWithMessagesByUserId(@Param("userId") long userId);
}
