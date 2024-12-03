package de.tum.cit.aet.artemis.atlas.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATLAS;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

@Controller
@Profile(PROFILE_ATLAS)
public class CompetencyApi extends AbstractAtlasApi {

    private final CompetencyService competencyService;

    public CompetencyApi(CompetencyService competencyService) {
        this.competencyService = competencyService;
    }

    public void addCompetencyLinksToExerciseUnits(Lecture lecture) {
        competencyService.addCompetencyLinksToExerciseUnits(lecture);
    }
}
