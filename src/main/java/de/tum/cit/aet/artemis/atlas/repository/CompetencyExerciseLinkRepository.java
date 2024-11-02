package de.tum.cit.aet.artemis.atlas.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface CompetencyExerciseLinkRepository extends ArtemisJpaRepository<CompetencyExerciseLink, Long> {

}
