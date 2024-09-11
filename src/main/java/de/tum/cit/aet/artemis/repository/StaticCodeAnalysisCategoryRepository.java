package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the StaticCodeAnalysisCategory entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface StaticCodeAnalysisCategoryRepository extends ArtemisJpaRepository<StaticCodeAnalysisCategory, Long> {

    Set<StaticCodeAnalysisCategory> findByExerciseId(long exerciseId);
}
