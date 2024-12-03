package de.tum.cit.aet.artemis.atlas.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATLAS;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;

@Controller
@Profile(PROFILE_ATLAS)
public class CourseCompetencyApi extends AbstractAtlasApi {

    private final CourseCompetencyRepository courseCompetencyRepository;

    public CourseCompetencyApi(CourseCompetencyRepository courseCompetencyRepository) {
        this.courseCompetencyRepository = courseCompetencyRepository;
    }

    public void save(CourseCompetency courseCompetency) {
        courseCompetencyRepository.save(courseCompetency);
    }
}
