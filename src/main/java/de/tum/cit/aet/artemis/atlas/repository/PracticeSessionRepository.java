package de.tum.cit.aet.artemis.atlas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.practice.PracticeSession;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface PracticeSessionRepository extends ArtemisJpaRepository<PracticeSession, Long> {

    @Query("""
            SELECT ps
            FROM PracticeSession ps
            WHERE ps.user.id = :userId
                AND ps.course.id = :courseId
            ORDER BY ps.startTime DESC
            """)
    List<PracticeSession> findAllByUserIdAndCourseIdOrderByStartTimeDesc(@Param("userId") long userId, @Param("courseId") long courseId);

    @Query("""
            SELECT ps
            FROM PracticeSession ps
            WHERE ps.id = :sessionId
                AND ps.course.id = :courseId
                AND ps.user.id = :userId
            """)
    Optional<PracticeSession> findByIdAndCourseIdAndUserId(@Param("sessionId") long sessionId, @Param("courseId") long courseId, @Param("userId") long userId);

    default PracticeSession findByIdAndCourseIdAndUserIdOrElseThrow(long sessionId, long courseId, long userId) {
        return getValueElseThrow(findByIdAndCourseIdAndUserId(sessionId, courseId, userId), sessionId);
    }
}
