package de.tum.cit.aet.artemis.atlas.api;

import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;

@Controller
@ConditionalOnProperty(name = "artemis.atlas.enabled", havingValue = "true")
public class CompetencyRepositoryApi extends AbstractAtlasApi {

    private final CompetencyRepository competencyRepository;

    public CompetencyRepositoryApi(CompetencyRepository competencyRepository) {
        this.competencyRepository = competencyRepository;
    }

    public Set<Competency> findAllByCourseId(long courseId) {
        return competencyRepository.findAllByCourseId(courseId);
    }
}
