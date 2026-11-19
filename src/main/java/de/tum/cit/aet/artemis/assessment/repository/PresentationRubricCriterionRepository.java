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

import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricCriterion;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the PresentationRubricCriterion entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PresentationRubricCriterionRepository extends ArtemisJpaRepository<PresentationRubricCriterion, Long> {

    List<PresentationRubricCriterion> findAllByPresentationAssessmentIdOrderByPosition(long presentationAssessmentId);

    /**
     * Deletes the rubric of a presentation assessment. The database deletes the levels of the criteria with them.
     *
     * @param assessmentId the presentation assessment id
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM PresentationRubricCriterion criterion
            WHERE criterion.presentationAssessment.id = :assessmentId
            """)
    void deleteAllByPresentationAssessmentId(@Param("assessmentId") long assessmentId);
}
