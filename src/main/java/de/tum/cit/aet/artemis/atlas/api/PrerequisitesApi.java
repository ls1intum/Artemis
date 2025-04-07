package de.tum.cit.aet.artemis.atlas.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.atlas.repository.PrerequisiteRepository;
import de.tum.cit.aet.artemis.core.domain.Course;

@Controller
@Conditional(AtlasEnabled.class)
public class PrerequisitesApi extends AbstractAtlasApi {

    private final PrerequisiteRepository prerequisiteRepository;

    public PrerequisitesApi(PrerequisiteRepository prerequisiteRepository) {
        this.prerequisiteRepository = prerequisiteRepository;
    }

    public long countByCourse(Course course) {
        return prerequisiteRepository.countByCourse(course);
    }

    public void deleteAll(Iterable<Prerequisite> prerequisites) {
        prerequisiteRepository.deleteAll(prerequisites);
    }
}
