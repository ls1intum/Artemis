package de.tum.cit.aet.artemis.atlas.api;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;

/**
 * Exposes aggregated competency numbers to callers outside the atlas module, namely the public usage dashboard.
 * Only counts are exposed, never learner progress.
 */
@Conditional(AtlasEnabled.class)
@Controller
@Lazy
public class CompetencyUsageStatisticsApi extends AbstractAtlasApi {

    private final CompetencyRepository competencyRepository;

    public CompetencyUsageStatisticsApi(CompetencyRepository competencyRepository) {
        this.competencyRepository = competencyRepository;
    }

    /**
     * @param courseIds the courses
     * @return the number of competencies of these courses, without prerequisites
     */
    public long countCompetencies(Set<Long> courseIds) {
        return competencyRepository.countByCourseIdIn(courseIds);
    }
}
