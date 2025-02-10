package de.tum.cit.aet.artemis.atlas.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.AtlasConfig;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

@Controller
@ConditionalOnBean(AtlasConfig.class)
public class CompetencyApi extends AbstractAtlasApi {

    private final CompetencyService competencyService;

    public CompetencyApi(CompetencyService competencyService) {
        this.competencyService = competencyService;
    }

    public void addCompetencyLinksToExerciseUnits(Lecture lecture) {
        competencyService.addCompetencyLinksToExerciseUnits(lecture);
    }
}
