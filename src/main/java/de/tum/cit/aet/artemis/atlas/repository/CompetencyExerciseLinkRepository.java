package de.tum.cit.aet.artemis.atlas.repository;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Repository
public interface CompetencyExerciseLinkRepository extends ArtemisJpaRepository<CompetencyExerciseLink, Long> {

}
