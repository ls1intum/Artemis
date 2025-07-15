package de.tum.cit.aet.artemis.atlas.test_repository;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyProgressRepository;

@Repository
@Primary
public interface CompetencyProgressTestRepository extends CompetencyProgressRepository {

    default CompetencyProgress findByCompetencyIdAndUserIdOrElseThrow(long competencyId, long userId) {
        return getValueElseThrow(findByCompetencyIdAndUserId(competencyId, userId));
    }
}
