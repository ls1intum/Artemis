package de.tum.cit.aet.artemis.core.test_repository;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageRequest;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageRequestRepository;

@Lazy
@Repository
@Primary
public interface LLMTokenUsageRequestTestRepository extends LLMTokenUsageRequestRepository {

    /**
     * Find all LLM token usage requests for traces belonging to a given course.
     * This method is only used in tests to verify deletion.
     *
     * @param courseId the id of the course
     * @return list of LLM token usage requests for traces in the course
     */
    @Query("SELECT r FROM LLMTokenUsageRequest r WHERE r.trace.courseId = :courseId")
    List<LLMTokenUsageRequest> findAllByTraceCourseId(@Param("courseId") long courseId);
}
