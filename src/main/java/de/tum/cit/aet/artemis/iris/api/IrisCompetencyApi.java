package de.tum.cit.aet.artemis.iris.api;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisCompetencyRecommendationDTO;
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

    public void executeCompetencyExtractionPipeline(User user, Course course, String courseDescription, List<IrisCompetencyRecommendationDTO> currentCompetencies) {
        var pyrisCompetencies = currentCompetencies.stream()
                .map(competency -> new PyrisCompetencyRecommendationDTO(competency.title(), competency.description(), competency.taxonomy()))
                .toArray(PyrisCompetencyRecommendationDTO[]::new);
        irisCompetencyGenerationService.executeCompetencyExtractionPipeline(user, course, courseDescription, pyrisCompetencies);
    }
}
