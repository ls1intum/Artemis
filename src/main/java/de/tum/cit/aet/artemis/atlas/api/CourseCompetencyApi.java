package de.tum.cit.aet.artemis.atlas.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;

@Controller
@Conditional(AtlasEnabled.class)
public class CourseCompetencyApi extends AbstractAtlasApi {

    private final CourseCompetencyRepository courseCompetencyRepository;

    public CourseCompetencyApi(CourseCompetencyRepository courseCompetencyRepository) {
        this.courseCompetencyRepository = courseCompetencyRepository;
    }

    public void save(CourseCompetency courseCompetency) {
        courseCompetencyRepository.save(courseCompetency);
    }
}
