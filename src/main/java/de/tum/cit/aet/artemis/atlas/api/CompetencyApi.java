package de.tum.cit.aet.artemis.atlas.api;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

@Conditional(AtlasEnabled.class)
@Controller
public class CompetencyApi extends AbstractAtlasApi {

    private final CompetencyService competencyService;

    private final CompetencyRepository competencyRepository;

    public CompetencyApi(CompetencyService competencyService, CompetencyRepository competencyRepository) {
        this.competencyService = competencyService;
        this.competencyRepository = competencyRepository;
    }

    public void addCompetencyLinksToExerciseUnits(Lecture lecture) {
        competencyService.addCompetencyLinksToExerciseUnits(lecture);
    }

    public Set<Competency> findAllByCourseId(long courseId) {
        return competencyRepository.findAllByCourseId(courseId);
    }
}
