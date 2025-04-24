package de.tum.cit.aet.artemis.atlas.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

@Conditional(AtlasEnabled.class)
@Controller
public class CompetencyApi extends AbstractAtlasApi {

    private final CompetencyService competencyService;

    public CompetencyApi(CompetencyService competencyService) {
        this.competencyService = competencyService;
    }

    public void addCompetencyLinksToExerciseUnits(Lecture lecture) {
        competencyService.addCompetencyLinksToExerciseUnits(lecture);
    }
}
