package de.tum.cit.aet.artemis.atlas.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.core.repository.base.AbstractSimpleRepository;

@Profile(PROFILE_CORE)
@Service
public class CompetencySimpleRepository extends AbstractSimpleRepository {

    private final static String ENTITY_NAME = "Competency";

    private final CompetencyRepository competencyRepository;

    public CompetencySimpleRepository(CompetencyRepository competencyRepository) {
        this.competencyRepository = competencyRepository;
    }

    public Competency findByIdWithLectureUnitsAndExercisesElseThrow(long competencyId) {
        return getValueElseThrow(competencyRepository.findByIdWithLectureUnitsAndExercises(competencyId), ENTITY_NAME);
    }

    public Competency findByIdWithLectureUnitsElseThrow(long competencyId) {
        return getValueElseThrow(competencyRepository.findByIdWithLectureUnitsAndExercises(competencyId), ENTITY_NAME);
    }
}
