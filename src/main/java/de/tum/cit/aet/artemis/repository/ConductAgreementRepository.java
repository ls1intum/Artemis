package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.domain.ConductAgreement;
import de.tum.cit.aet.artemis.domain.ConductAgreementId;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the Code of Conduct Agreement entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ConductAgreementRepository extends ArtemisJpaRepository<ConductAgreement, ConductAgreementId> {

    /**
     * Find the user's agreement to a course's code of conduct.
     *
     * @param courseId the ID of the code of conduct's course
     * @param userId   the user's ID
     * @return the user's agreement to the course's code of conduct
     */
    Optional<ConductAgreement> findByCourseIdAndUserId(Long courseId, Long userId);

    /**
     * Delete all users' agreements to a course's code of conduct.
     *
     * @param courseId the ID of the code of conduct's course
     */
    @Transactional // ok because of delete
    @Modifying
    void deleteByCourseId(Long courseId);
}
