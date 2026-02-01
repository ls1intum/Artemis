package de.tum.cit.aet.artemis.iris.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.IrisCompetencyGenerationService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyRecommendationDTO;

@Conditional(IrisEnabled.class)
@Controller
@Lazy
public class IrisCompetencyApi extends AbstractIrisApi {

    private final IrisCompetencyGenerationService irisCompetencyGenerationService;

    public IrisCompetencyApi(IrisCompetencyGenerationService irisCompetencyGenerationService) {
        this.irisCompetencyGenerationService = irisCompetencyGenerationService;
    }

    public void executeCompetencyExtractionPipeline(User user, Course course, String courseDescription, PyrisCompetencyRecommendationDTO[] currentCompetencies) {
        irisCompetencyGenerationService.executeCompetencyExtractionPipeline(user, course, courseDescription, currentCompetencies);
    }
}
