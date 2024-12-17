package de.tum.cit.aet.artemis.atlas.repository.simple;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.core.repository.base.AbstractSimpleService;

@Profile(PROFILE_CORE)
@Service
public class CompetencySimpleService extends AbstractSimpleService<Competency> {

    private final CompetencyRepository competencyRepository;

    @Override
    protected String getEntityName() {
        return "Competency";
    }

    public CompetencySimpleService(CompetencyRepository competencyRepository) {
        this.competencyRepository = competencyRepository;
    }

    public Competency findByIdWithLectureUnitsAndExercisesElseThrow(long competencyId) {
        return getValueElseThrow(competencyRepository.findByIdWithLectureUnitsAndExercises(competencyId));
    }

    public Competency findByIdWithLectureUnitsElseThrow(long competencyId) {
        return getValueElseThrow(competencyRepository.findByIdWithLectureUnitsAndExercises(competencyId));
    }
}
