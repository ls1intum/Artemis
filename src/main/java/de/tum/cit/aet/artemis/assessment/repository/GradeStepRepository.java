package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.GradeStep;

/**
 * Spring Data JPA Repository for the GradeStep entity
 */
@Profile(PROFILE_CORE)
@Repository
public interface GradeStepRepository extends ArtemisJpaRepository<GradeStep, Long> {

}
