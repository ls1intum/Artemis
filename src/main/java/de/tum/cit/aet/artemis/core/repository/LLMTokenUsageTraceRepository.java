package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface LLMTokenUsageTraceRepository extends ArtemisJpaRepository<LLMTokenUsageTrace, Long> {

    /**
     * Count the number of LLM token usage traces for a given course.
     *
     * @param courseId the id of the course
     * @return the number of LLM token usage traces in the course
     */
    @Query("SELECT COUNT(t) FROM LLMTokenUsageTrace t WHERE t.courseId = :courseId")
    long countByCourseId(@Param("courseId") long courseId);

    /**
     * Find all LLM token usage traces with their requests for a given course.
     *
     * @param courseId the id of the course
     * @return list of LLM token usage traces with requests in the course
     */
    @Query("SELECT DISTINCT t FROM LLMTokenUsageTrace t LEFT JOIN FETCH t.llmRequests WHERE t.courseId = :courseId")
    List<LLMTokenUsageTrace> findAllWithRequestsByCourseId(@Param("courseId") long courseId);

    @Query("""
            SELECT COALESCE(ROUND(SUM((req.numInputTokens * req.costPerMillionInputTokens / 1000000) +
                            (req.numOutputTokens * req.costPerMillionOutputTokens / 1000000)), 2), 0.0)
            FROM LLMTokenUsageRequest req
            WHERE req.trace.courseId = :courseId
            """)
    Double calculateTotalLlmCostInEurForCourse(@Param("courseId") Long courseId);

    /**
     * Deletes all LLM token usage traces for a given course.
     *
     * @param courseId The ID of the course.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM LLMTokenUsageTrace t WHERE t.courseId = :courseId")
    void deleteAllByCourseId(@Param("courseId") Long courseId);
}
