package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.FeedbackItemId;
import de.tum.cit.aet.artemis.assessment.domain.ScaFeedback;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the {@link ScaFeedback} entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ScaFeedbackRepository extends ArtemisJpaRepository<ScaFeedback, FeedbackItemId> {

    @Query("""
            SELECT feedback
            FROM ScaFeedback feedback
                LEFT JOIN FETCH feedback.message
            WHERE feedback.id.resultId = :resultId
            """)
    List<ScaFeedback> findWithMessageByResultId(@Param("resultId") long resultId);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM ScaFeedback feedback
            WHERE feedback.id.resultId = :resultId
            """)
    void deleteByResultId(@Param("resultId") long resultId);
}
