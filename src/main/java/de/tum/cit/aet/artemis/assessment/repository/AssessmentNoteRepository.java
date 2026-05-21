package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentNote;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the {@link AssessmentNote} entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface AssessmentNoteRepository extends ArtemisJpaRepository<AssessmentNote, Long> {

    /**
     * Deletes all assessment notes belonging to a result.
     * Used by {@link de.tum.cit.aet.artemis.assessment.service.ResultService#deleteResult ResultService.deleteResult}
     * Path 2 to clean up assessment notes before the JPQL bulk delete of the result itself.
     *
     * @param resultId the id of the result whose assessment notes should be deleted
     */
    @Modifying
    @Transactional // ok because of delete
    void deleteByResultId(long resultId);
}
