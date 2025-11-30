package de.tum.cit.aet.artemis.atlas.api;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;

@Controller
@Conditional(AtlasEnabled.class)
@Lazy
public class CompetencyRepositoryApi extends AbstractAtlasApi {

    private final CompetencyRepository competencyRepository;

    private final CourseCompetencyRepository courseCompetencyRepository;

    public CompetencyRepositoryApi(CompetencyRepository competencyRepository, CourseCompetencyRepository courseCompetencyRepository) {
        this.competencyRepository = competencyRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
    }

    public Set<Competency> findAllCompetenciesByCourseId(long courseId) {
        return competencyRepository.findAllByCourseId(courseId);
    }

    public CourseCompetency findCompetencyOrPrerequisiteByIdElseThrow(long competencyId) {
        return courseCompetencyRepository.findByIdElseThrow(competencyId);
    }
}
