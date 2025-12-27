package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageRequest;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface LLMTokenUsageRequestRepository extends ArtemisJpaRepository<LLMTokenUsageRequest, Long> {

    /**
     * Deletes all LLM token usage requests for traces belonging to a given course.
     * This must be called before deleting the traces to avoid foreign key constraint violations.
     *
     * @param courseId The ID of the course.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM LLMTokenUsageRequest r WHERE r.trace.courseId = :courseId")
    void deleteAllByTraceCourseId(@Param("courseId") Long courseId);
}
