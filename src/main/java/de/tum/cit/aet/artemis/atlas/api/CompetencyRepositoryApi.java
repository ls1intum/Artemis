package de.tum.cit.aet.artemis.atlas.api;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;

@Controller
@Conditional(AtlasEnabled.class)
@Lazy
public class CompetencyRepositoryApi extends AbstractAtlasApi {

    private final CompetencyRepository competencyRepository;

    public CompetencyRepositoryApi(CompetencyRepository competencyRepository) {
        this.competencyRepository = competencyRepository;
    }

    public Set<Competency> findAllByCourseId(long courseId) {
        return competencyRepository.findAllByCourseId(courseId);
    }

    public Competency findByIdElseThrow(long competencyId) {
        return competencyRepository.findByIdElseThrow(competencyId);
    }
}
