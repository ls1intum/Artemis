package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;

/**
 * Spring Data repository for the AuxiliaryRepository entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface AuxiliaryRepositoryRepository extends ArtemisJpaRepository<AuxiliaryRepository, Long> {

    List<AuxiliaryRepository> findByExerciseId(Long exerciseId);

}
