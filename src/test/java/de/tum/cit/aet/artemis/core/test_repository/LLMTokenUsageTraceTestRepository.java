package de.tum.cit.aet.artemis.core.test_repository;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageTraceRepository;

@Lazy
@Repository
@Primary
public interface LLMTokenUsageTraceTestRepository extends LLMTokenUsageTraceRepository {

    /**
     * Find all LLM token usage traces for a given course.
     * This method is only used in tests to verify deletion.
     *
     * @param courseId the id of the course
     * @return list of LLM token usage traces in the course
     */
    @Query("SELECT t FROM LLMTokenUsageTrace t WHERE t.courseId = :courseId")
    List<LLMTokenUsageTrace> findAllByCourseId(@Param("courseId") long courseId);
}
