package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.VcsAnalyticsLog;

@Profile(PROFILE_LOCALVC)
@Lazy
@Repository
public interface VcsAnalyticsLogRepository extends ArtemisJpaRepository<VcsAnalyticsLog, Long> {

    /**
     * Retrieves last entry of masked user Id
     *
     * @return latest vcsAnalyticsLog entry
     */
    @Query("""
                SELECT vcsAnalyticsLog
                FROM VcsAnalyticsLog vcsAnalyticsLog
                WHERE vcsAnalyticsLog.maskedUserId = :maskedUserId
                ORDER BY vcsAnalyticsLog.timestamp DESC
                LIMIT 1
            """)
    Optional<VcsAnalyticsLog> findLatestByMaskedUserId(@Param("maskedUserId") String maskedUserId);

    /**
     * Retrieves the courseId for given participationId
     *
     */
    @Query(value = """
            SELECT COALESCE(e.course_id, ex.course_id)
            FROM participation p
            LEFT JOIN exercise e ON p.exercise_id = e.id
            LEFT JOIN exercise_group eg ON e.exercise_group_id = eg.id
            LEFT JOIN exam ex ON eg.exam_id = ex.id
            WHERE p.id = :participationId
            """, nativeQuery = true)
    Optional<Long> findCourseIdByParticipationId(@Param("participationId") Long participationId);
}
