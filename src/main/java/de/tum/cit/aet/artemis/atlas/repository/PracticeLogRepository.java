package de.tum.cit.aet.artemis.atlas.repository;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.practice.PracticeLog;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface PracticeLogRepository extends ArtemisJpaRepository<PracticeLog, Long> {

    @Query("""
            SELECT pl
            FROM PracticeLog pl
            LEFT JOIN FETCH pl.quizQuestion
            WHERE pl.practiceSession.id = :sessionId
            ORDER BY pl.id ASC
            """)
    List<PracticeLog> findAllByPracticeSessionIdWithQuestion(@Param("sessionId") long sessionId);

    List<PracticeLog> findAllByPracticeSessionIdOrderByIdAsc(long practiceSessionId);
}
