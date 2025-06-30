package de.tum.cit.aet.artemis.iris.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionDAO;

@Lazy
@Repository
@Profile(PROFILE_IRIS)
public interface IrisChatSessionRepository extends ArtemisJpaRepository<IrisChatSession, Long> {

    /**
     * Finds a list of {@link IrisChatSession} based on the course and user ID.
     *
     * @param courseId The ID of the course.
     * @param userId   The ID of the user.
     * @return A list of chat sessions sorted by creation date in descending order.
     */
    @Query("""
            SELECT s
            FROM IrisChatSession s
            LEFT JOIN IrisCourseChatSession ccs ON s.id = ccs.id
            LEFT JOIN IrisLectureChatSession lcs ON s.id = lcs.id
            LEFT JOIN IrisTextExerciseChatSession tecs ON s.id = tecs.id
            LEFT JOIN IrisProgrammingExerciseChatSession pecs ON s.id = pecs.id
            LEFT JOIN Lecture l ON l.id = lcs.lectureId
            LEFT JOIN Exercise e ON e.id = tecs.exerciseId OR e.id = pecs.exerciseId
            WHERE s.userId = :userId
              AND (ccs.courseId = :courseId OR l.course.id = :courseId OR e.course.id = :courseId)
            ORDER BY s.creationDate DESC
            """)
    List<IrisChatSessionDAO> findByCourseIdAndUserId(@Param("courseId") long courseId, @Param("userId") long userId);
}
