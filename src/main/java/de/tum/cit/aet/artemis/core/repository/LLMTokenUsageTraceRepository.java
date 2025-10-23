package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface LLMTokenUsageTraceRepository extends ArtemisJpaRepository<LLMTokenUsageTrace, Long> {

    @Query("""
            SELECT COALESCE(ROUND(SUM((req.numInputTokens * req.costPerMillionInputTokens / 1000000) +
                            (req.numOutputTokens * req.costPerMillionOutputTokens / 1000000)), 2), 0.0)
            FROM LLMTokenUsageRequest req
            WHERE req.trace.courseId = :courseId
            """)
    Double calculateTotalLlmCostInEurForCourse(@Param("courseId") Long courseId);
}
