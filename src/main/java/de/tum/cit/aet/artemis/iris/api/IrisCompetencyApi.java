package de.tum.cit.aet.artemis.iris.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.service.IrisCompetencyGenerationService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyRecommendationDTO;

@Profile(PROFILE_IRIS)
@Controller
public class IrisCompetencyApi extends AbstractIrisApi {

    private final IrisCompetencyGenerationService irisCompetencyGenerationService;

    public IrisCompetencyApi(IrisCompetencyGenerationService irisCompetencyGenerationService) {
        this.irisCompetencyGenerationService = irisCompetencyGenerationService;
    }

    public void executeCompetencyExtractionPipeline(User user, Course course, String courseDescription, PyrisCompetencyRecommendationDTO[] currentCompetencies) {
        irisCompetencyGenerationService.executeCompetencyExtractionPipeline(user, course, courseDescription, currentCompetencies);
    }
}
